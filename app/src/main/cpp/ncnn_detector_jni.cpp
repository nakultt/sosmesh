/**
 * NCNN YOLOv8 Detector — JNI bridge for Android
 *
 * Loads the RoadEye NCNN model from Android assets,
 * runs inference on Bitmap frames, and returns detection results.
 */
#include <jni.h>
#include <android/bitmap.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>

#include <net.h>
#include <vector>
#include <algorithm>
#include <cmath>
#include <string>

#define TAG "NcnnDetector"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ── Global state ─────────────────────────────────────────────────────────────
static ncnn::Net* g_net = nullptr;
static int g_input_size = 640;
static const int NUM_CLASSES = 12;

// ── Detection result ─────────────────────────────────────────────────────────
struct DetResult {
    int   class_id;
    float confidence;
    float x1, y1, x2, y2;
};

// ── IoU helper ───────────────────────────────────────────────────────────────
static float iou(const DetResult& a, const DetResult& b) {
    float xx1 = std::max(a.x1, b.x1);
    float yy1 = std::max(a.y1, b.y1);
    float xx2 = std::min(a.x2, b.x2);
    float yy2 = std::min(a.y2, b.y2);
    float w   = std::max(0.f, xx2 - xx1);
    float h   = std::max(0.f, yy2 - yy1);
    float inter = w * h;
    float area_a = (a.x2 - a.x1) * (a.y2 - a.y1);
    float area_b = (b.x2 - b.x1) * (b.y2 - b.y1);
    return inter / (area_a + area_b - inter + 1e-5f);
}

// ── NMS ──────────────────────────────────────────────────────────────────────
static void nms_sorted(std::vector<DetResult>& dets, float threshold) {
    std::sort(dets.begin(), dets.end(),
              [](const DetResult& a, const DetResult& b) { return a.confidence > b.confidence; });

    std::vector<bool> suppressed(dets.size(), false);
    for (size_t i = 0; i < dets.size(); i++) {
        if (suppressed[i]) continue;
        for (size_t j = i + 1; j < dets.size(); j++) {
            if (suppressed[j]) continue;
            if (iou(dets[i], dets[j]) > threshold)
                suppressed[j] = true;
        }
    }
    std::vector<DetResult> kept;
    kept.reserve(dets.size());
    for (size_t i = 0; i < dets.size(); i++)
        if (!suppressed[i]) kept.push_back(dets[i]);
    dets.swap(kept);
}

// ── clamp helper ─────────────────────────────────────────────────────────────
static inline float clampf(float v, float lo, float hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

// ═══════════════════════════════════════════════════════════════════════════════
//  JNI: nativeInit
// ═══════════════════════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jboolean JNICALL
Java_com_meshsos_domain_detection_NcnnDetector_nativeInit(
        JNIEnv* env, jobject /*thiz*/, jobject assetManager, jint inputSize)
{
    if (g_net) { delete g_net; g_net = nullptr; }
    g_input_size = inputSize;

    g_net = new ncnn::Net();
    g_net->opt.use_vulkan_compute = false;
    g_net->opt.num_threads = 4;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) { LOGE("AAssetManager_fromJava failed"); return JNI_FALSE; }

    int rp = g_net->load_param(mgr, "model.ncnn.param");
    int rm = g_net->load_model(mgr, "model.ncnn.bin");
    if (rp != 0 || rm != 0) {
        LOGE("load_param=%d  load_model=%d", rp, rm);
        delete g_net; g_net = nullptr;
        return JNI_FALSE;
    }
    LOGI("Model loaded OK  (input %d×%d, %d classes)", g_input_size, g_input_size, NUM_CLASSES);
    return JNI_TRUE;
}

// ═══════════════════════════════════════════════════════════════════════════════
//  JNI: nativeDetect
//  Returns float[]:  [N, cls,conf,x1,y1,x2,y2, cls,conf,... ]
//  Coordinates are in original-image pixel space.
// ═══════════════════════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_meshsos_domain_detection_NcnnDetector_nativeDetect(
        JNIEnv* env, jobject /*thiz*/, jobject bitmap,
        jfloat confThreshold, jfloat nmsThreshold)
{
    if (!g_net) { LOGE("Model not initialised"); return nullptr; }

    // ── Lock bitmap pixels ──────────────────────────────────────────────────
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != 0) return nullptr;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format %d unsupported (need RGBA_8888)", info.format);
        return nullptr;
    }
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != 0) return nullptr;

    int img_w = (int)info.width;
    int img_h = (int)info.height;

    // ── Letterbox preprocess (matching Python detect_service.py) ─────────────
    float scale = std::min((float)g_input_size / img_h,
                           (float)g_input_size / img_w);
    int new_w = (int)(img_w * scale);
    int new_h = (int)(img_h * scale);
    int pad_w = (g_input_size - new_w) / 2;
    int pad_h = (g_input_size - new_h) / 2;

    ncnn::Mat in_rgb = ncnn::Mat::from_pixels_resize(
            (const unsigned char*)pixels, ncnn::Mat::PIXEL_RGBA2RGB,
            img_w, img_h, new_w, new_h);

    AndroidBitmap_unlockPixels(env, bitmap);

    ncnn::Mat in_pad;
    ncnn::copy_make_border(in_rgb, in_pad,
                           pad_h, g_input_size - new_h - pad_h,
                           pad_w, g_input_size - new_w - pad_w,
                           ncnn::BORDER_CONSTANT, 114.f);

    const float norm[3] = {1.f / 255.f, 1.f / 255.f, 1.f / 255.f};
    in_pad.substract_mean_normalize(nullptr, norm);

    // ── Inference ───────────────────────────────────────────────────────────
    ncnn::Extractor ex = g_net->create_extractor();
    ex.input("in0", in_pad);

    ncnn::Mat out;
    ex.extract("out0", out);

    // ── Postprocess ─────────────────────────────────────────────────────────
    std::vector<DetResult> dets;
    int expected_dims = 4 + NUM_CLASSES;  // 16

    // Determine layout --------------------------------------------------
    // Case A: (N, 6) — end-to-end model with [x1,y1,x2,y2,conf,cls]
    if (out.dims == 2 && (out.w == 6 || out.h == 6)) {
        int rows = out.h, cols = out.w;
        bool col6 = (cols == 6);
        int n = col6 ? rows : cols;
        for (int i = 0; i < n; i++) {
            float vals[6];
            if (col6) {
                const float* row = out.row(i);
                for (int j = 0; j < 6; j++) vals[j] = row[j];
            } else {
                for (int j = 0; j < 6; j++) vals[j] = out.row(j)[i];
            }
            float x1, y1, x2, y2, conf;
            int cls_id;
            if (vals[4] < 1.0f && vals[5] > 1.0f) {
                x1 = vals[0]; y1 = vals[1]; x2 = vals[2]; y2 = vals[3];
                conf = vals[4]; cls_id = (int)vals[5];
            } else {
                x1 = vals[0]; y1 = vals[1]; x2 = vals[2]; y2 = vals[3];
                cls_id = (int)vals[4]; conf = vals[5];
            }
            if (conf < confThreshold) continue;
            dets.push_back({cls_id, conf,
                            (x1 - pad_w) / scale, (y1 - pad_h) / scale,
                            (x2 - pad_w) / scale, (y2 - pad_h) / scale});
        }
    }
    // Case B: YOLOv8 raw output — (expected_dims, N) or (1, expected_dims, N)
    else {
        int rows, cols;
        if (out.dims == 3) {
            // (c, h, w) — for YOLOv8 NCNN export typically c = expected_dims
            if (out.c == expected_dims) {
                rows = out.c;
                cols = out.h * out.w;
            } else {
                // (1, expected_dims, N) — squeeze batch
                rows = out.h;
                cols = out.w;
            }
        } else {
            rows = out.h;
            cols = out.w;
        }

        bool transposed = false;
        int num_candidates;
        if (rows == expected_dims) {
            num_candidates = cols;
        } else if (cols == expected_dims) {
            num_candidates = rows;
            transposed = true;
        } else {
            LOGE("Unexpected output %d×%d (expected one dim = %d)", rows, cols, expected_dims);
            return nullptr;
        }

        // Lambda to read a value from the 2-D or 3-D mat
        // dim = feature index [0..expected_dims), idx = candidate index
        auto val = [&](int dim, int idx) -> float {
            if (out.dims == 3 && out.c == expected_dims) {
                // channel-major: channel(dim), linear index idx
                return ((const float*)out.channel(dim))[idx];
            }
            if (!transposed)
                return out.row(dim)[idx];
            else
                return out.row(idx)[dim];
        };

        for (int i = 0; i < num_candidates; i++) {
            float cx = val(0, i);
            float cy = val(1, i);
            float bw = val(2, i);
            float bh = val(3, i);

            int   best_cls   = 0;
            float best_score = 0;
            for (int c = 0; c < NUM_CLASSES; c++) {
                float s = val(4 + c, i);
                if (s > best_score) { best_score = s; best_cls = c; }
            }
            if (best_score < confThreshold) continue;

            float x1 = cx - bw * 0.5f;
            float y1 = cy - bh * 0.5f;
            float x2 = cx + bw * 0.5f;
            float y2 = cy + bh * 0.5f;

            // Map back from letterboxed coords → original image
            float ox1 = clampf((x1 - pad_w) / scale, 0.f, (float)img_w);
            float oy1 = clampf((y1 - pad_h) / scale, 0.f, (float)img_h);
            float ox2 = clampf((x2 - pad_w) / scale, 0.f, (float)img_w);
            float oy2 = clampf((y2 - pad_h) / scale, 0.f, (float)img_h);

            dets.push_back({best_cls, best_score, ox1, oy1, ox2, oy2});
        }
    }

    // ── NMS ──────────────────────────────────────────────────────────────────
    nms_sorted(dets, nmsThreshold);

    // ── Pack into float array ────────────────────────────────────────────────
    int n = (int)dets.size();
    int len = 1 + n * 6;
    jfloatArray result = env->NewFloatArray(len);
    if (!result) return nullptr;

    std::vector<float> buf(len);
    buf[0] = (float)n;
    for (int i = 0; i < n; i++) {
        int o = 1 + i * 6;
        buf[o + 0] = (float)dets[i].class_id;
        buf[o + 1] = dets[i].confidence;
        buf[o + 2] = dets[i].x1;
        buf[o + 3] = dets[i].y1;
        buf[o + 4] = dets[i].x2;
        buf[o + 5] = dets[i].y2;
    }
    env->SetFloatArrayRegion(result, 0, len, buf.data());
    return result;
}

// ═══════════════════════════════════════════════════════════════════════════════
//  JNI: nativeDestroy
// ═══════════════════════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL
Java_com_meshsos_domain_detection_NcnnDetector_nativeDestroy(
        JNIEnv* /*env*/, jobject /*thiz*/)
{
    delete g_net;
    g_net = nullptr;
    LOGI("Detector destroyed");
}
