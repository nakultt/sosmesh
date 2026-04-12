#!/usr/bin/env python3
"""
PACER Pi Detection Service
===========================
Main detection loop that runs on the Raspberry Pi.

Captures frames from the camera, runs YOLO inference, applies violation rules,
and sends detected violations to the PACER backend via POST /api/events.

Usage:
    python detect_service.py

Environment variables (see pi_config.py for all options):
    PACER_BACKEND_URL     — Backend server URL (default: http://localhost:8000)
    PACER_CAMERA_ID       — Unique camera identifier
    PACER_GPS_LAT         — Deployment latitude
    PACER_GPS_LNG         — Deployment longitude
    PACER_LOCATION_LABEL  — Human-readable location name
    PACER_SHOW_PREVIEW    — Show annotated frame window (true/false)
"""

from __future__ import annotations

import json
import logging
import os
import sys
import tempfile
import time
from datetime import datetime, timezone
from dataclasses import dataclass

import cv2
import requests
import numpy as np
import ncnn

import pi_config as cfg
from violation_rules import analyze_detections
from offline_queue import save_to_queue, flush_queue, queue_size

# ─── Logging ─────────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("pacer.detect")

# ─── Cooldown Tracker ────────────────────────────────────────────────────────────
# Tracks last send time per violation_type to avoid flooding
_last_sent: dict[str, float] = {}

# ─── RoadEye Classes ─────────────────────────────────────────────────────────────────
CLASS_NAMES = [
    "driver_with_helmet",
    "bike",
    "driver",
    "passenger_with_helemt",
    "passenger",
    "driver_without_helmet",
    "passenger_without_helemt",
    "cow",
    "horse",
    "elephant",
    "pothole",
    "wheeling"
]

@dataclass
class MockYoloBoxes:
    cls: list[int]
    conf: list[float]
    xyxy: list[list[float]]

    def __len__(self):
        return len(self.cls)

    def __iter__(self):
        return iter(range(len(self)))

    def __getitem__(self, idx):
        return self

@dataclass
class MockYoloResult:
    boxes: MockYoloBoxes
    names: dict[int, str]

def _is_on_cooldown(violation_type: str) -> bool:
    """Check if this violation type was sent too recently."""
    last = _last_sent.get(violation_type, 0)
    return (time.time() - last) < cfg.COOLDOWN_SECONDS


def _mark_sent(violation_type: str):
    _last_sent[violation_type] = time.time()


def _send_to_backend(image_path: str, event_data: dict) -> bool:
    """
    Send a violation event to the PACER backend.
    Returns True on success, False on failure (event is queued).
    """
    try:
        with open(image_path, "rb") as img_file:
            files = {"image": ("frame.jpg", img_file, "image/jpeg")}
            data = {"data": json.dumps(event_data, default=str)}

            response = requests.post(
                cfg.EVENTS_ENDPOINT,
                files=files,
                data=data,
                timeout=15,
            )

        if response.status_code == 200:
            result = response.json()
            status = result.get("status", "")
            if status == "deduplicated":
                logger.info(f"  → Deduplicated by backend")
            else:
                vid = result.get("violation_id", "?")
                logger.info(f"  → Sent OK (violation_id: {vid})")
            return True
        else:
            logger.warning(f"  → Backend returned HTTP {response.status_code}")
            save_to_queue(image_path, event_data)
            return False

    except requests.ConnectionError:
        logger.warning(f"  → Backend unreachable, saving to offline queue")
        save_to_queue(image_path, event_data)
        return False
    except Exception as e:
        logger.error(f"  → Send failed: {e}")
        save_to_queue(image_path, event_data)
        return False


def preprocess(image, input_size):
    """Preprocess image for NCNN inference."""
    h, w = image.shape[:2]
    scale = min(input_size[0] / h, input_size[1] / w)
    new_h, new_w = int(h * scale), int(w * scale)
    resized = cv2.resize(image, (new_w, new_h))

    padded = np.full((input_size[0], input_size[1], 3), 114, dtype=np.uint8)
    pad_h = (input_size[0] - new_h) // 2
    pad_w = (input_size[1] - new_w) // 2
    padded[pad_h:pad_h + new_h, pad_w:pad_w + new_w] = resized

    mat_in = ncnn.Mat.from_pixels(padded, ncnn.Mat.PixelType.PIXEL_BGR2RGB, padded.shape[1], padded.shape[0])
    
    mean_vals = [0.0, 0.0, 0.0]
    norm_vals = [1/255.0, 1/255.0, 1/255.0]
    mat_in.substract_mean_normalize(mean_vals, norm_vals)

    return mat_in, scale, pad_h, pad_w


def postprocess_to_results(mat_out, scale, pad_h, pad_w, conf_threshold, nms_threshold):
    """Process NCNN output and format it into a MockYoloResult for compatibility with analyze_detections."""
    output = np.array(mat_out)
    filtered_boxes = []
    filtered_confidences = []
    filtered_class_ids = []
    
    expected_dims = 4 + len(CLASS_NAMES)

    if len(output.shape) == 2 and output.shape[1] == 6:
        for det in output:
            # Different model exports can have slightly different column orders
            if det[4] < 1.0 and det[5] > 1.0: 
                x1, y1, x2, y2, conf, cls_id = det
            else: 
                x1, y1, x2, y2, cls_id, conf = det
            
            if conf < conf_threshold:
                continue
            
            filtered_boxes.append([x1, y1, x2, y2])
            filtered_confidences.append(conf)
            filtered_class_ids.append(int(cls_id))

    elif len(output.shape) == 3 or (len(output.shape) == 2 and output.shape[0] == expected_dims):
        if len(output.shape) == 3:
            output = output[0]
        if output.shape[0] == expected_dims:
            output = output.T

        boxes = output[:, :4]
        scores = output[:, 4:]
        class_ids = np.argmax(scores, axis=1)
        confidences = scores[np.arange(len(scores)), class_ids]

        mask = confidences > conf_threshold
        boxes = boxes[mask]
        confidences = confidences[mask]
        class_ids = class_ids[mask]

        if len(boxes) > 0:
            cx, cy, w, h = boxes[:, 0], boxes[:, 1], boxes[:, 2], boxes[:, 3]
            x1 = cx - w / 2
            y1 = cy - h / 2
            x2 = cx + w / 2
            y2 = cy + h / 2
            
            filtered_boxes = np.column_stack((x1, y1, x2, y2)).tolist()
            filtered_confidences = confidences.tolist()
            filtered_class_ids = class_ids.tolist()

    if not filtered_boxes:
        return [MockYoloResult(boxes=MockYoloBoxes(cls=[], conf=[], xyxy=[]), names={i: name for i, name in enumerate(CLASS_NAMES)})]

    boxes = np.array(filtered_boxes)
    confidences = np.array(filtered_confidences)
    class_ids = np.array(filtered_class_ids)

    x = boxes[:, 0]
    y = boxes[:, 1]
    w = boxes[:, 2] - boxes[:, 0]
    h = boxes[:, 3] - boxes[:, 1]
    nms_boxes = np.column_stack((x, y, w, h)).tolist()

    indices = cv2.dnn.NMSBoxes(nms_boxes, confidences.tolist(), conf_threshold, nms_threshold)
    
    final_cls = []
    final_conf = []
    final_xyxy = []

    if len(indices) > 0:
        for i in indices:
            idx = i if isinstance(i, np.integer) or isinstance(i, int) else i[0]
            x1, y1, x2, y2 = filtered_boxes[idx]
            conf = confidences[idx]
            cls_id = class_ids[idx]
            
            orig_x1 = (x1 - pad_w) / scale
            orig_y1 = (y1 - pad_h) / scale
            orig_x2 = (x2 - pad_w) / scale
            orig_y2 = (y2 - pad_h) / scale
            
            final_cls.append(int(cls_id))
            final_conf.append(float(conf))
            final_xyxy.append(np.array([orig_x1, orig_y1, orig_x2, orig_y2]))

    mock_boxes = MockYoloBoxes(cls=final_cls, conf=final_conf, xyxy=final_xyxy)
    mock_names = {i: name for i, name in enumerate(CLASS_NAMES)}
    
    return [MockYoloResult(boxes=mock_boxes, names=mock_names)]


def run_detection_loop():
    """Main detection loop — capture frames, run YOLO NCNN, send violations."""
    logger.info(f"Loading NCNN model from {cfg.PARAM_PATH} and {cfg.BIN_PATH}...")
    
    net = ncnn.Net()
    net.opt.use_vulkan_compute = False 
    net.load_param(cfg.PARAM_PATH)
    net.load_model(cfg.BIN_PATH)

    logger.info("NCNN Model loaded successfully")

    # Open camera
    logger.info(f"Opening camera {cfg.CAMERA_INDEX}...")
    cap = cv2.VideoCapture(cfg.CAMERA_INDEX)
    if not cap.isOpened():
        logger.error("Failed to open camera. Exiting.")
        sys.exit(1)
    logger.info("Camera ready")

    # Startup info
    logger.info("=" * 60)
    logger.info(f"  PACER Detection Service (NCNN-Optimized)")
    logger.info(f"  Camera ID:  {cfg.CAMERA_ID}")
    logger.info(f"  Location:   {cfg.LOCATION_LABEL}")
    logger.info(f"  GPS:        {cfg.GPS_LAT}, {cfg.GPS_LNG}")
    logger.info(f"  Backend:    {cfg.BACKEND_URL}")
    logger.info(f"  Interval:   {cfg.DETECTION_INTERVAL}s")
    logger.info(f"  Cooldown:   {cfg.COOLDOWN_SECONDS}s")
    logger.info(f"  Preview:    {cfg.SHOW_PREVIEW}")
    logger.info("=" * 60)

    # Flush any existing offline queue on startup
    queued = queue_size()
    if queued > 0:
        logger.info(f"Flushing {queued} queued events...")
        flush_queue()

    frame_count = 0
    violations_sent = 0

    try:
        while True:
            start_time = time.time()
            
            ret, frame = cap.read()
            if not ret:
                logger.warning("Failed to read frame, retrying...")
                time.sleep(1)
                continue

            frame_count += 1

            # NCNN Inference
            mat_in, scale, pad_h, pad_w = preprocess(frame, (cfg.INPUT_SIZE, cfg.INPUT_SIZE))

            with net.create_extractor() as ex:
                ex.input("in0", mat_in)
                _, mat_out = ex.extract("out0")

            # Format NCNN output to MockYoloResult
            results = postprocess_to_results(
                mat_out, scale, pad_h, pad_w, 
                cfg.CONFIDENCE_THRESHOLD, cfg.NMS_THRESHOLD
            )

            # Analyze detections for violations
            violations = analyze_detections(results)

            # Show preview if enabled
            if cfg.SHOW_PREVIEW:
                annotated = frame.copy()
                # Draw boxes
                for i, cls_id in enumerate(results[0].boxes.cls):
                    box = results[0].boxes.xyxy[i]
                    conf = results[0].boxes.conf[i]
                    name = CLASS_NAMES[cls_id] if cls_id < len(CLASS_NAMES) else f"cls_{cls_id}"
                    
                    x1, y1, x2, y2 = map(int, box)
                    cv2.rectangle(annotated, (x1, y1), (x2, y2), (0, 255, 0), 2)
                    cv2.putText(annotated, f"{name} {conf:.2f}", (x1, max(y1 - 10, 0)), 
                                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 2)
                
                # Overlay violation info
                for i, v in enumerate(violations):
                    text = f"{v['violation_type']} ({v['confidence']:.2f})"
                    cv2.putText(annotated, text, (10, 30 + i * 25),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)
                cv2.imshow("PACER Detection", annotated)
                if cv2.waitKey(1) & 0xFF == ord("q"):
                    logger.info("Preview window closed by user")
                    break

            # Process each detected violation
            for violation in violations:
                vtype = violation["violation_type"]

                # Check cooldown
                if _is_on_cooldown(vtype):
                    continue

                logger.info(f"[VIOLATION] {vtype} (conf: {violation['confidence']:.2f})")

                # Save frame to temp file
                tmp_path = os.path.join(
                    tempfile.gettempdir(),
                    f"pacer_{int(time.time())}_{vtype}.jpg",
                )
                cv2.imwrite(tmp_path, frame)

                # Strip internal fields from bounding boxes
                clean_boxes = []
                for box in violation.get("bounding_boxes", []):
                    clean_boxes.append({
                        "x": box["x"], "y": box["y"],
                        "w": box["w"], "h": box["h"],
                        "label": box["label"],
                        "confidence": box["confidence"],
                    })

                # Build event data
                event_data = {
                    "violation_type": vtype,
                    "confidence": violation["confidence"],
                    "timestamp": datetime.now(timezone.utc).isoformat(),
                    "camera_source": cfg.CAMERA_SOURCE,
                    "camera_id": cfg.CAMERA_ID,
                    "gps_lat": cfg.GPS_LAT,
                    "gps_lng": cfg.GPS_LNG,
                    "location_label": cfg.LOCATION_LABEL,
                    "bounding_boxes": clean_boxes,
                }

                # Send to backend
                _send_to_backend(tmp_path, event_data)
                _mark_sent(vtype)
                violations_sent += 1

                # Clean up temp file
                try:
                    os.remove(tmp_path)
                except OSError:
                    pass

            # Periodically flush offline queue (every 100 frames)
            if frame_count % 100 == 0:
                queued = queue_size()
                if queued > 0:
                    logger.info(f"Periodic queue flush: {queued} events pending")
                    flush_queue()

            # Throttle detection rate based on loop execution time
            elapsed = time.time() - start_time
            sleep_time = max(0, cfg.DETECTION_INTERVAL - elapsed)
            time.sleep(sleep_time)

    except KeyboardInterrupt:
        logger.info("Shutting down...")
    finally:
        cap.release()
        if cfg.SHOW_PREVIEW:
            cv2.destroyAllWindows()
        logger.info(f"Session stats: {frame_count} frames, {violations_sent} violations sent")


if __name__ == "__main__":
    run_detection_loop()
