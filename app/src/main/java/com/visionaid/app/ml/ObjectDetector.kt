package com.visionaid.app.ml

import android.graphics.Bitmap
import android.graphics.RectF
import com.visionaid.app.data.models.AppSettings
import com.visionaid.app.data.models.DetectedObject
import com.visionaid.app.domain.models.SpeechPriority
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.Constants

/**
 * Main object-detection orchestrator (spec 3.4).
 *
 * Runs the YOLO processor on a frame, enriches results with the
 * DirectionEstimator, and generates SMART voice messages:
 *  - no repeated announcements of the same object within 3 seconds
 *  - dangerous/close objects are prioritized (URGENT/HIGH)
 *  - multiple objects are grouped into one sentence
 *  - global throttle of at most one announcement per 2 seconds
 *
 * The generated message is spoken by the pipeline (ProcessFrameUseCase);
 * this class only produces it.
 */
class ObjectDetector(
    private val yoloProcessor: YoloV11Processor,
    private val directionEstimator: DirectionEstimator
) {

    // --- announcement bookkeeping ---
    private val lastAnnouncedPerObject = HashMap<String, Long>()
    private var lastAnnouncementAt = 0L

    /** Load the model (idempotent). Call once at startup. */
    fun initialize() {
        yoloProcessor.load()
    }

    val isModelAvailable: Boolean get() = yoloProcessor.isModelAvailable
    val classLabels: List<String> get() = yoloProcessor.classLabels

    /**
     * Runs detection and returns enriched objects. Boxes are normalized to
     * the frame (stretch mapping from the square model input).
     */
    fun detect(bitmap: Bitmap, settings: AppSettings): List<DetectedObject> {
        val now = System.currentTimeMillis()
        val raw = yoloProcessor.detect(
            bitmap,
            confidenceThreshold = settings.detectionConfidenceThreshold,
            iouThreshold = Constants.DEFAULT_IOU_THRESHOLD
        )
        return raw.map { d ->
            val box = RectF(
                (d.cx - d.width / 2f).coerceIn(0f, 1f),
                (d.cy - d.height / 2f).coerceIn(0f, 1f),
                (d.cx + d.width / 2f).coerceIn(0f, 1f),
                (d.cy + d.height / 2f).coerceIn(0f, 1f)
            )
            directionEstimator.enrich(yoloProcessor.labelFor(d.classId), d.score, box, now)
        }
    }

    /**
     * Builds a throttled voice announcement from [objects] following the
     * spec's priority rules. Returns null when nothing should be spoken.
     */
    fun buildAnnouncement(objects: List<DetectedObject>, settings: AppSettings): Announcement? {
        if (objects.isEmpty()) return null
        val now = System.currentTimeMillis()

        // 1. Global throttle (spec: max 1 announcement / 2 s, configurable).
        val globalCooldown = maxOf(
            settings.announcementCooldown.toLong(),
            Constants.GLOBAL_ANNOUNCE_THROTTLE_MS
        )
        if (now - lastAnnouncementAt < globalCooldown) return null

        // 2. Drop objects announced within the per-object cooldown (3 s).
        val fresh = objects.filter { obj ->
            val key = "${obj.label}:${obj.direction}"
            val last = lastAnnouncedPerObject[key] ?: 0L
            now - last >= Constants.OBJECT_ANNOUNCE_COOLDOWN_MS
        }
        if (fresh.isEmpty()) return null

        // 3. Prioritize: dangerous & close > close > new > far.
        val ranked = fresh.sortedWith(
            compareByDescending<DetectedObject> { it.isDangerous && it.distance == "very close" }
                .thenByDescending { it.distance != "far" }
                .thenByDescending { it.confidence }
        )
        val topDanger = ranked.firstOrNull { it.isDangerous && it.distance == "very close" }

        val message: String
        val priority: SpeechPriority
        if (topDanger != null) {
            // Spec 3.3: urgent warning for dangerous close objects.
            message = "WARNING: ${topDanger.label} very close, " +
                "${directionEstimator.directionPhrase(topDanger.direction)}!"
            priority = SpeechPriority.URGENT
        } else {
            message = groupObjects(ranked)
            priority = if (ranked.any { it.distance == "very close" }) {
                SpeechPriority.HIGH
            } else {
                SpeechPriority.NORMAL
            }
        }

        // 4. Record announcement bookkeeping.
        lastAnnouncementAt = now
        fresh.forEach { lastAnnouncedPerObject["${it.label}:${it.direction}"] = now }

        return Announcement(message, priority)
    }

    /**
     * Groups objects into a single natural sentence (spec 3.4c):
     * "There are 3 people ahead of you and a car to your left."
     */
    private fun groupObjects(objects: List<DetectedObject>): String {
        val parts = ArrayList<String>(4)
        val remaining = objects.take(MAX_ANNOUNCED_OBJECTS).toMutableList()

        // Group people into one phrase.
        val people = remaining.filter { it.label == "person" }
        if (people.isNotEmpty()) {
            val direction = people.groupBy { it.direction }.maxByOrNull { it.value.size }!!.key
            parts.add(
                if (people.size == 1) {
                    "One person ${phrase(people.first())}"
                } else {
                    "There are ${people.size} people ${directionEstimator.directionPhrase(direction)}"
                }
            )
            remaining.removeAll { it.label == "person" }
        }

        // Remaining objects, closest/most dangerous first.
        remaining.sortedByDescending { it.areaFraction }.forEach { obj ->
            parts.add(capitalize(obj.label) + " " + phrase(obj))
        }

        return parts.joinToString(
            separator = if (parts.size > 2) ". " else ", and ",
            postfix = "."
        ).replace("..", ".")
    }

    private fun phrase(obj: DetectedObject): String {
        val distance = if (obj.distance == "far") "in the distance" else obj.distance
        return "$distance ${directionEstimator.directionPhrase(obj.direction)}"
    }

    private fun capitalize(text: String): String =
        text.replaceFirstChar { it.uppercase() }

    /** A voice announcement with its priority. */
    data class Announcement(val text: String, val priority: SpeechPriority)

    companion object {
        private const val TAG = "ObjectDetector"
        private const val MAX_ANNOUNCED_OBJECTS = 5
    }
}
