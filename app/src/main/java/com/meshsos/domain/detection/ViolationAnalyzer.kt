package com.meshsos.domain.detection

/**
 * Violation Rules Engine — Kotlin port of violation_rules.py
 *
 * Maps fine-tuned YOLO detections to PACER violation types using
 * the same heuristic logic as the Pi reference implementation.
 *
 * ┌─────────────────────┬──────────────────────────────────────────┐
 * │ Violation Type      │ Detection Rule                           │
 * ├─────────────────────┼──────────────────────────────────────────┤
 * │ helmet_absence      │ driver_without_helmet or                 │
 * │                     │ passenger_without_helemt                 │
 * │ triple_riding       │ bike + ≥3 riders overlapping bike box    │
 * │ animal_crossing     │ cow / horse / elephant detected          │
 * │ pothole             │ pothole detected                         │
 * │ wheelie             │ wheeling detected                        │
 * └─────────────────────┴──────────────────────────────────────────┘
 */
class ViolationAnalyzer {

    // ── Confidence thresholds (match Python) ─────────────────────────────────
    private val riderConfThreshold = 0.25f
    private val otherConfThreshold = 0.45f

    // ── Public API ───────────────────────────────────────────────────────────

    fun analyze(detections: List<Detection>): List<Violation> {
        if (detections.isEmpty()) return emptyList()

        val bikes = mutableListOf<BoxInfo>()
        val riders = mutableListOf<BoxInfo>()
        val noHelmetRiders = mutableListOf<BoxInfo>()
        val animals = mutableListOf<BoxInfo>()
        val potholes = mutableListOf<BoxInfo>()
        val wheelies = mutableListOf<BoxInfo>()

        for (det in detections) {
            val isRider = det.className in RoadEyeClasses.RIDERS

            // Stricter threshold for non-riders, looser for riders
            if (!isRider && det.confidence < otherConfThreshold) continue
            if (isRider && det.confidence < riderConfThreshold) continue

            val box = det.toBoxInfo()

            when {
                det.className == "bike"                        -> bikes.add(box)
                det.className in RoadEyeClasses.RIDERS         -> {
                    riders.add(box)
                    if (det.className in RoadEyeClasses.NO_HELMET) noHelmetRiders.add(box)
                }
                det.className == "pothole"                     -> potholes.add(box)
                det.className == "wheeling"                    -> wheelies.add(box)
                det.className in RoadEyeClasses.ANIMALS        -> animals.add(box)
            }
        }

        val violations = mutableListOf<Violation>()

        // 1. Pothole
        potholes.forEach { p ->
            violations.add(Violation(
                type = Violation.POTHOLE,
                confidence = p.confidence,
                boundingBoxes = listOf(p.toBoundingBox())
            ))
        }

        // 2. Wheelie
        wheelies.forEach { w ->
            violations.add(Violation(
                type = Violation.WHEELIE,
                confidence = w.confidence,
                boundingBoxes = listOf(w.toBoundingBox())
            ))
        }

        // 3. Helmet absence
        noHelmetRiders.forEach { rider ->
            violations.add(Violation(
                type = Violation.HELMET_ABSENCE,
                confidence = rider.confidence,
                boundingBoxes = listOf(rider.toBoundingBox())
            ))
        }

        // 4. Triple riding
        for (bike in bikes) {
            val bikeRiders = riders.filter { isRiderOnBike(it, bike) }
            if (bikeRiders.size >= 3) {
                val avgConf = bikeRiders.map { it.confidence }.average().toFloat()
                val combined = minOf(avgConf, bike.confidence)
                val boxes = listOf(bike.toBoundingBox()) + bikeRiders.map { it.toBoundingBox() }
                violations.add(Violation(
                    type = Violation.TRIPLE_RIDING,
                    confidence = combined.round3(),
                    boundingBoxes = boxes
                ))
            }
        }

        // 5. Animal crossing
        if (animals.isNotEmpty()) {
            val best = animals.maxByOrNull { it.confidence }!!
            violations.add(Violation(
                type = Violation.ANIMAL_CROSSING,
                confidence = best.confidence.round3(),
                boundingBoxes = animals.map { it.toBoundingBox() }
            ))
        }

        return violations
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private data class BoxInfo(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val label: String, val confidence: Float
    ) {
        fun toBoundingBox() = BoundingBox(x, y, w, h, label, confidence.round3())
    }

    private fun Detection.toBoxInfo() = BoxInfo(
        x = x1, y = y1, w = x2 - x1, h = y2 - y1,
        label = className, confidence = confidence
    )

    /**
     * Check horizontal overlap ratio (matching Python _boxes_overlap_horizontally).
     */
    private fun boxesOverlapHorizontally(a: BoxInfo, b: BoxInfo, threshold: Float = 0.3f): Boolean {
        val ax1 = a.x; val ax2 = a.x + a.w
        val bx1 = b.x; val bx2 = b.x + b.w
        val overlap = maxOf(0f, minOf(ax2, bx2) - maxOf(ax1, bx1))
        val minW = minOf(a.w, b.w)
        if (minW == 0f) return false
        return (overlap / minW) >= threshold
    }

    /**
     * Check if a rider bounding box is positioned 'on' a bike (matching Python _is_rider_on_bike).
     * Loosened thresholds to catch third passengers.
     */
    private fun isRiderOnBike(rider: BoxInfo, bike: BoxInfo): Boolean {
        if (!boxesOverlapHorizontally(rider, bike, threshold = 0.05f)) return false

        val riderBottom = rider.y + rider.h
        val bikeTop = bike.y
        val bikeBottom = bike.y + bike.h

        return riderBottom >= (bikeTop - bike.h * 0.2f) &&
               riderBottom <= (bikeBottom + bike.h * 0.6f)
    }

    private fun Float.round3() = (this * 1000).toInt() / 1000f
}
