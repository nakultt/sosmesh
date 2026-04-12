"""
PACER Pi Detection Service — Offline Queue
Stores violation events locally when the backend server is unreachable.
Retries sending when connectivity is restored.
"""

from __future__ import annotations

import base64
import json
import logging
import os
import time
from typing import Optional

import requests

import pi_config as cfg

logger = logging.getLogger("pacer.queue")


def _ensure_queue_dir():
    os.makedirs(cfg.QUEUE_DIR, exist_ok=True)


def save_to_queue(image_path: str, event_data: dict):
    """Save a failed event to the offline queue for later retry."""
    _ensure_queue_dir()

    try:
        with open(image_path, "rb") as f:
            image_b64 = base64.b64encode(f.read()).decode("utf-8")

        queue_item = {
            **event_data,
            "image_base64": image_b64,
        }

        filename = f"{int(time.time())}_{event_data.get('violation_type', 'unknown')}.json"
        filepath = os.path.join(cfg.QUEUE_DIR, filename)

        with open(filepath, "w") as f:
            json.dump(queue_item, f)

        logger.info(f"Saved to offline queue: {filename}")

    except Exception as e:
        logger.error(f"Failed to save to queue: {e}")


def flush_queue() -> int:
    """
    Try to send all queued events to the backend.
    Returns number of successfully sent events.
    """
    _ensure_queue_dir()
    queue_files = sorted(f for f in os.listdir(cfg.QUEUE_DIR) if f.endswith(".json"))

    if not queue_files:
        return 0

    logger.info(f"Flushing offline queue: {len(queue_files)} events")
    sent = 0

    for filename in queue_files:
        filepath = os.path.join(cfg.QUEUE_DIR, filename)
        try:
            with open(filepath, "r") as f:
                item = json.load(f)

            # Use batch endpoint for queued items
            response = requests.post(
                cfg.BATCH_ENDPOINT,
                json={"events": [item]},
                timeout=30,
            )

            if response.status_code == 200:
                os.remove(filepath)
                sent += 1
                logger.info(f"Queue item sent: {filename}")
            else:
                logger.warning(f"Queue flush failed for {filename}: HTTP {response.status_code}")
                break  # Stop if server is rejecting — don't flood

        except requests.ConnectionError:
            logger.warning("Backend unreachable — stopping queue flush")
            break
        except Exception as e:
            logger.error(f"Queue flush error for {filename}: {e}")

    logger.info(f"Queue flush complete: {sent}/{len(queue_files)} sent")
    return sent


def queue_size() -> int:
    """Return number of events in the offline queue."""
    _ensure_queue_dir()
    return len([f for f in os.listdir(cfg.QUEUE_DIR) if f.endswith(".json")])
