package com.visionaid.app.utils

/**
 * Central place for every tunable constant used across the app.
 * Values here mirror the defaults defined in the project spec.
 */
object Constants {

    // ====================================================================
    // ESP32-CAM (MJPEG stream + touch sensor)
    // ====================================================================
    const val ESP32_DEFAULT_IP = "192.168.4.1"
    const val ESP32_DEFAULT_STREAM_PORT = 81
    const val ESP32_DEFAULT_HTTP_PORT = 80
    const val ESP32_STREAM_PATH = "/stream"
    const val ESP32_TOUCH_STATUS_PATH = "/touch_status"

    /** Poll interval for the touch-sensor HTTP endpoint (spec: 500 ms). */
    const val TOUCH_POLL_INTERVAL_MS = 500L
    const val TOUCH_LONG_PRESS_MS = 1_000L
    const val TOUCH_DOUBLE_TAP_WINDOW_MS = 400L
    const val TOUCH_SINGLE_TAP_CONFIRM_MS = 350L

    /** Max reconnect backoff for the MJPEG stream. */
    const val RECONNECT_BASE_DELAY_MS = 1_000L
    const val RECONNECT_MAX_DELAY_MS = 30_000L

    // ====================================================================
    // Machine learning (YOLO / OCR / VLM)
    // ====================================================================
    /** Preferred model (fp16 keeps float32 I/O — easiest to consume). */
    const val YOLO_MODEL_FILE = "models/yolo11n_float16.tflite"
    /** Full-int8 export (smaller + faster, needs quantized I/O handling). */
    const val YOLO_INT8_MODEL_FILE = "models/yolo11n_full_integer_quant.tflite"
    const val COCO_LABELS_FILE = "models/coco_labels.txt"

    const val DEFAULT_CONFIDENCE_THRESHOLD = 0.45f
    const val DEFAULT_IOU_THRESHOLD = 0.5f
    const val DEFAULT_INPUT_SIZE = 320

    /** OCR runs on every Nth processed frame (spec: every 3rd frame). */
    const val OCR_FRAME_INTERVAL = 3
    /** A text block must cover this fraction of frame height to be a "sign". */
    const val SIGN_MIN_HEIGHT_FRACTION = 0.10f
    /** Same text is not re-announced within this window (spec: 10 s). */
    const val OCR_REPEAT_COOLDOWN_MS = 10_000L

    /** Periodic VLM scene description interval (spec: every 30 s). */
    const val VLM_PERIODIC_INTERVAL_MS = 30_000L
    /** Scene descriptions are cached for this long (spec: 10 s). */
    const val VLM_CACHE_MS = 10_000L
    /** Max Gemini calls per minute (spec: 10). */
    const val VLM_RATE_LIMIT_PER_MINUTE = 10

    // ====================================================================
    // Voice announcements
    // ====================================================================
    /** Same object is not re-announced within this window (spec: 3 s). */
    const val OBJECT_ANNOUNCE_COOLDOWN_MS = 3_000L
    /** Global minimum spacing between announcements (spec: 2 s). */
    const val GLOBAL_ANNOUNCE_THROTTLE_MS = 2_000L
    /** LOW priority messages are dropped when this many are queued. */
    const val MAX_PENDING_LOW_PRIORITY = 3

    /** Bounding box area fractions used for distance estimation. */
    const val DISTANCE_VERY_CLOSE_AREA = 0.40f
    const val DISTANCE_NEARBY_AREA = 0.15f

    /** COCO classes that represent a collision hazard. */
    val DANGEROUS_OBJECT_CLASSES = setOf(
        "car", "truck", "bus", "motorcycle", "bicycle", "train", "knife", "fire hydrant"
    )

    // ====================================================================
    // Fall detection
    // ====================================================================
    /** Sensor sampling that balances latency vs. battery (~50 Hz). */
    const val SENSOR_SAMPLING_DELAY_US = 20_000 // SensorManager.SENSOR_DELAY_GAME
    /** All fall phases must occur within this window (spec: 3 s). */
    const val FALL_DETECTION_WINDOW_MS = 3_000L

    // ====================================================================
    // Emergency protocol
    // ====================================================================
    const val FALL_ALERT_COUNTDOWN_DEFAULT = 30
    const val SOS_COUNTDOWN_SECONDS = 10
    /** "Are you okay?" listening round duration. */
    const val EMERGENCY_LISTEN_MS = 3_500L
    /** Re-check the user for absence of movement after an alert (spec: 5 min). */
    const val FOLLOW_UP_MONITORING_MS = 5 * 60_000L

    // ====================================================================
    // Voice commands (wake word + intents)
    // ====================================================================
    val WAKE_WORDS = listOf("hey vision", "hey, vision", "heyvision", "vision")
    val CANCEL_PHRASES = listOf("i'm fine", "im fine", "i am fine", "fine", "cancel", "stop alert")
    val CONFIRM_PHRASES = listOf("help", "send alert", "send the alert", "yes", "emergency")

    // ====================================================================
    // Battery / power management (spec 16.3)
    // ====================================================================
    const val BATTERY_SAVER_THRESHOLD = 30   // reduce fps, disable VLM
    const val BATTERY_MINIMAL_THRESHOLD = 15 // critical detection + fall only
    const val BATTERY_CRITICAL_THRESHOLD = 10 // emergency features only

    // ====================================================================
    // Gemini API
    // ====================================================================
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"
    const val GEMINI_DEFAULT_MODEL = "gemini-2.5-flash"
    const val GEMINI_PLACEHOLDER_KEY = "YOUR_GEMINI_API_KEY_HERE"
    const val GEMINI_MAX_OUTPUT_TOKENS = 220

    // ====================================================================
    // Notifications
    // ====================================================================
    const val NOTIFICATION_CHANNEL_SERVICE = "visionaid_service"
    const val NOTIFICATION_CHANNEL_ALERTS = "visionaid_alerts"
    const val NOTIFICATION_ID_MAIN = 1001
    const val NOTIFICATION_ID_FALL = 1002
    const val NOTIFICATION_ID_BLUETOOTH = 1003

    // ====================================================================
    // Misc
    // ====================================================================
    /** Max conversation exchanges kept for assistant context (spec: 10). */
    const val ASSISTANT_HISTORY_SIZE = 10
    /** Max entries kept in the on-screen detection log. */
    const val DETECTION_LOG_MAX = 100
    /** Default TTS speech rate (spec: 1.2x). */
    const val DEFAULT_VOICE_SPEED = 1.2f
}
