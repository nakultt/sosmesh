"""
PACER Pi Detection Service — Configuration
All settings for the Raspberry Pi edge detection unit.
"""

import os

# ─── Backend Server ──────────────────────────────────────────────────────────────
BACKEND_URL = "http://localhost:8000"
EVENTS_ENDPOINT = f"{BACKEND_URL}/api/events"
BATCH_ENDPOINT = f"{BACKEND_URL}/api/events/batch"

# ─── Camera ──────────────────────────────────────────────────────────────────────
CAMERA_ID = os.getenv("PACER_CAMERA_ID", "pi-cam-001")
CAMERA_SOURCE = os.getenv("PACER_CAMERA_SOURCE", "pi_camera")
CAMERA_INDEX = int(os.getenv("PACER_CAMERA_INDEX", "0"))  # /dev/video0

# ─── GPS Location (set per deployment) ───────────────────────────────────────────
GPS_LAT = float(os.getenv("PACER_GPS_LAT", "13.0827"))
GPS_LNG = float(os.getenv("PACER_GPS_LNG", "80.2707"))
LOCATION_LABEL = os.getenv("PACER_LOCATION_LABEL", "Deployment Location")

# ─── YOLO Model ──────────────────────────────────────────────────────────────────
MODEL_DIR = os.getenv("PACER_MODEL_DIR", "./RoadEye_ncnn_model")
PARAM_PATH = os.path.join(MODEL_DIR, "model.ncnn.param")
BIN_PATH = os.path.join(MODEL_DIR, "model.ncnn.bin")
# Lowered initial confidence from 0.45 to 0.25 here so YOLO physically extracts the "looser" third-passengers.
# The violation engine (`violation_rules.py`) will artificially filter bikes/potholes/wheelies back to 0.45 
# so we don't get general false positives, but riders keep the 0.25 looseness.
CONFIDENCE_THRESHOLD = float(os.getenv("PACER_CONFIDENCE", "0.25"))
NMS_THRESHOLD = float(os.getenv("PACER_NMS", "0.45"))
INPUT_SIZE = int(os.getenv("PACER_INPUT_SIZE", "640"))

# ─── Detection Loop ─────────────────────────────────────────────────────────────
# Seconds between each frame analysis (controls CPU load on Pi)
DETECTION_INTERVAL = float(os.getenv("PACER_DETECTION_INTERVAL", "1.0"))

# Minimum seconds between sending the same violation type
# (prevents flooding backend with duplicate detections)
COOLDOWN_SECONDS = int(os.getenv("PACER_COOLDOWN_SECONDS", "30"))

# ─── Offline Queue ───────────────────────────────────────────────────────────────
QUEUE_DIR = os.getenv("PACER_QUEUE_DIR", "./offline_queue")

# ─── Display ─────────────────────────────────────────────────────────────────────
# Set to True to show annotated frame preview (for debugging, disable in headless Pi)
SHOW_PREVIEW = os.getenv("PACER_SHOW_PREVIEW", "false").lower() == "true"
