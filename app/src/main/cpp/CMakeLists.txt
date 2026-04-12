cmake_minimum_required(VERSION 3.10)
project(ncnn_detector)

# ── Locate pre-built NCNN SDK (downloaded by download_ncnn_sdk.ps1) ──
set(NCNN_SDK_DIR "${CMAKE_SOURCE_DIR}/ncnn-sdk")

# Point CMake to the per-ABI config
set(ncnn_DIR "${NCNN_SDK_DIR}/${ANDROID_ABI}/lib/cmake/ncnn")
find_package(ncnn REQUIRED)

# ── JNI shared library ──
add_library(ncnn_detector SHARED ncnn_detector_jni.cpp)

target_link_libraries(ncnn_detector
    ncnn
    android       # AAssetManager
    log           # __android_log_print
    jnigraphics   # AndroidBitmap_*
)
