package com.meshsos.domain.detection

/**
 * A bounding box in XYWH format (matching the PACER backend schema).
 */
data class BoundingBox(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val label: String,
    val confidence: Float
)

/**
 * A detected traffic violation, composed of one or more bounding boxes.
 */
data class Violation(
    val type: String,
    val confidence: Float,
    val boundingBoxes: List<BoundingBox>
) {
    companion object {
        const val HELMET_ABSENCE  = "helmet_absence"
        const val TRIPLE_RIDING   = "triple_riding"
        const val ANIMAL_CROSSING = "animal_crossing"
        const val POTHOLE         = "pothole"
        const val WHEELIE         = "wheelie"
    }
}
