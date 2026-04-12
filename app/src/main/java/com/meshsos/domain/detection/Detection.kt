package com.meshsos.domain.detection

/**
 * A single YOLO detection from the NCNN model.
 * Coordinates are in original-image pixel space.
 */
data class Detection(
    val classId: Int,
    val className: String,
    val confidence: Float,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float
) {
    val width  get() = x2 - x1
    val height get() = y2 - y1
    val centerX get() = (x1 + x2) / 2f
    val centerY get() = (y1 + y2) / 2f
}

/**
 * RoadEye fine-tuned YOLO class names (12 classes).
 */
object RoadEyeClasses {

    val NAMES = listOf(
        "driver_with_helmet",       // 0
        "bike",                     // 1
        "driver",                   // 2
        "passenger_with_helemt",    // 3
        "passenger",                // 4
        "driver_without_helmet",    // 5
        "passenger_without_helemt", // 6
        "cow",                      // 7
        "horse",                    // 8
        "elephant",                 // 9
        "pothole",                  // 10
        "wheeling"                  // 11
    )

    fun nameOf(id: Int): String = NAMES.getOrElse(id) { "class_$id" }

    val ANIMALS = setOf("cow", "horse", "elephant")

    val RIDERS = setOf(
        "driver_with_helmet", "driver", "driver_without_helmet",
        "passenger_with_helemt", "passenger", "passenger_without_helemt"
    )

    val NO_HELMET = setOf("driver_without_helmet", "passenger_without_helemt")
}
