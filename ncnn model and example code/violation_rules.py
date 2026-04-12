"""
PACER Pi Detection Service — Violation Rules Engine
Maps fine-tuned custom YOLO detections to PACER violation types using heuristic rules.

Our YOLO natively detects:
- driver_with_helmet / driver_without_helmet
- passenger_with_helemt / passenger_without_helemt
- driver / passenger (generic status)
- bike, pothole, wheeling, cow, horse, elephant.

┌─────────────────────────────────────────────────────────────────────────┐
│ Violation Type       │ Detection Rule                                  │
├─────────────────────────────────────────────────────────────────────────┤
│ helmet_absence       │ Detected 'driver_without_helmet' or             │
│                      │ 'passenger_without_helemt'                      │
│ triple_riding        │ bike + 3 or more riders overlapping bike box    │
│ animal_crossing      │ cow/horse/elephant detected                     │
│ pothole              │ pothole detected                                │
│ wheelie              │ wheeling detected                               │
└─────────────────────────────────────────────────────────────────────────┘
"""

from __future__ import annotations
from typing import Optional


# Constants
CLASS_NAMES_ANIMALS = {"cow", "horse", "elephant"}
CLASS_NAMES_RIDERS = {
    "driver_with_helmet", "driver", "driver_without_helmet",
    "passenger_with_helemt", "passenger", "passenger_without_helemt"
}
CLASS_NAMES_NO_HELMET = {
    "driver_without_helmet", "passenger_without_helemt"
}


def _boxes_overlap_horizontally(box_a: dict, box_b: dict, threshold: float = 0.3) -> bool:
    """Check if two bounding boxes overlap horizontally (IoU on x-axis)."""
    ax1, ax2 = box_a["x"], box_a["x"] + box_a["w"]
    bx1, bx2 = box_b["x"], box_b["x"] + box_b["w"]
    overlap = max(0, min(ax2, bx2) - max(ax1, bx1))
    min_width = min(box_a["w"], box_b["w"])
    if min_width == 0:
        return False
    return (overlap / min_width) >= threshold


def _is_rider_on_bike(rider_box: dict, bike_box: dict) -> bool:
    """Check if a rider bounding box is positioned 'on' a bike."""
    rider_bottom = rider_box["y"] + rider_box["h"]
    bike_bottom = bike_box["y"] + bike_box["h"]
    bike_top = bike_box["y"]

    # LOOSENED: Rider should overlap horizontally with bike. 
    # Lowered threshold to 0.05 (just touching) to comfortably catch edge riders.
    if not _boxes_overlap_horizontally(rider_box, bike_box, threshold=0.05):
        return False

    # LOOSENED: Rider's bottom should be loosely within bike's vertical range.
    # We allow the rider to sit higher (up to 20% of bike height above bike) 
    # or legs to hang much lower (up to 60% of bike height below bike).
    return rider_bottom >= (bike_top - bike_box["h"] * 0.2) and rider_bottom <= (bike_bottom + bike_box["h"] * 0.6)


def _box_to_dict(box, cls_id: int, cls_name: str, conf: float) -> dict:
    """Convert a YOLO box to our standard dict format."""
    x1, y1, x2, y2 = box
    return {
        "x": float(x1),
        "y": float(y1),
        "w": float(x2 - x1),
        "h": float(y2 - y1),
        "label": cls_name,
        "confidence": round(float(conf), 3),
        "class_id": int(cls_id),
    }


def analyze_detections(results) -> list[dict]:
    """
    Analyze generic + fine-tuned YOLO detection results.
    Returns:
        List of violation dicts: {"violation_type": str, "confidence": float, "bounding_boxes": list}
    """
    if not results or len(results) == 0:
        return []

    result = results[0]
    boxes = result.boxes
    if boxes is None or len(boxes) == 0:
        return []

    bikes = []
    riders = []
    no_helmet_riders = []
    animals = []
    potholes = []
    wheelies = []

    for i in range(len(boxes)):
        cls_id = int(boxes.cls[i])
        conf = float(boxes.conf[i])
        xyxy = boxes.xyxy[i].tolist()
        cls_name = result.names.get(cls_id, f"class_{cls_id}").lower()

        # To allow "loose" triple riding, we must accept lower-confidence riders (e.g. ~0.25+),
        # but we want to retain strict confidence (e.g. ~0.45+) for everything else so we don't get false positives.
        # We assume the overall YOLO engine is now passing boxes with at least 0.25 confidence.
        is_rider = cls_name in CLASS_NAMES_RIDERS
        
        # Stricter threshold for non-riders to prevent false bounding boxes
        if not is_rider and conf < 0.45:
            continue
        # Looser threshold for riders to catch occluded 3rd passengers
        if is_rider and conf < 0.25:
            continue

        box_dict = _box_to_dict(xyxy, cls_id, cls_name, conf)

        if cls_name == "bike":
            bikes.append(box_dict)
        elif cls_name in CLASS_NAMES_RIDERS:
            riders.append(box_dict)
            if cls_name in CLASS_NAMES_NO_HELMET:
                no_helmet_riders.append(box_dict)
        elif cls_name == "pothole":
            potholes.append(box_dict)
        elif cls_name == "wheeling":
            wheelies.append(box_dict)
        elif cls_name in CLASS_NAMES_ANIMALS:
            animals.append(box_dict)

    violations = []

    # ─── 1. Pothole (Custom Class) ───────────────────────────────────────────
    for p in potholes:
        violations.append({
            "violation_type": "pothole",
            "confidence": p["confidence"],
            "bounding_boxes": [p],
        })

    # ─── 2. Wheelie (Custom Class) ───────────────────────────────────────────
    for w in wheelies:
        violations.append({
            "violation_type": "wheelie",
            "confidence": w["confidence"],
            "bounding_boxes": [w],
        })

    # ─── 3. Helmet Absence ───────────────────────────────────────────────────
    for rider in no_helmet_riders:
        violations.append({
            "violation_type": "helmet_absence",
            "confidence": rider["confidence"],
            "bounding_boxes": [rider],
        })

    # ─── 4. Triple Riding ───────────────────────────────────────────────
    for bike in bikes:
        bike_riders = [r for r in riders if _is_rider_on_bike(r, bike)]
        if len(bike_riders) >= 3:
            avg_conf = sum(r["confidence"] for r in bike_riders) / len(bike_riders)
            combined_conf = min(avg_conf, bike["confidence"])
            violations.append({
                "violation_type": "triple_riding",
                "confidence": round(combined_conf, 3),
                "bounding_boxes": [bike] + bike_riders,
            })

    # ─── 5. Animal Crossing ─────────────────────────────────────────────
    if animals:
        best_animal = max(animals, key=lambda a: a["confidence"])
        violations.append({
            "violation_type": "animal_crossing",
            "confidence": round(best_animal["confidence"], 3),
            "bounding_boxes": animals,
        })

    return violations
