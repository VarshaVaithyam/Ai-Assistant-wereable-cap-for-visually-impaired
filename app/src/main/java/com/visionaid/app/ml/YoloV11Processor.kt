package com.visionaid.app.ml

import android.content.Context
import android.graphics.Bitmap
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.Constants
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnapiDelegate
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * TensorFlow Lite wrapper for YOLOv11n object detection (spec 3.2).
 *
 * Model preparation (see docs/MODEL_PREPARATION.md):
 *   from ultralytics import YOLO
 *   YOLO("yolo11n.pt").export(format="tflite", imgsz=320, int8=False)
 *   -> copy yolo11n_float16.tflite into app/src/main/assets/models/
 *
 * The processor transparently supports:
 *  - GPU delegate (with NNAPI and CPU fallbacks)
 *  - float32 and uint8/int8 quantized input tensors
 *  - both Ultralytics output layouts: [1, 84, 8400] (values-major) and
 *    [1, 8400, 84] (anchors-major), with or without an objectness column
 *    (YOLOv5-style [1, 25200, 85])
 *
 * All public methods are thread-safe ([synchronized]) — inference must only
 * run on background threads.
 */
class YoloV11Processor(
    private val context: Context,
    private val modelFileName: String = Constants.YOLO_MODEL_FILE
) : Closeable {

    /** Raw detection in normalized (0–1) input-image coordinates. */
    data class RawDetection(
        val cx: Float,
        val cy: Float,
        val width: Float,
        val height: Float,
        val classId: Int,
        val score: Float
    )

    private var interpreter: Interpreter? = null
    private var delegate: Closeable? = null

    private var inputSize = Constants.DEFAULT_INPUT_SIZE
    private var isNchw = false
    private var isQuantizedInput = false
    private var inputScale = 1f
    private var inputZeroPoint = 0

    private var inputBuffer: ByteBuffer? = null

    // Output tensor metadata.
    private var outputNumAnchors = 0
    private var outputNumValues = 0
    private var outputAnchorsMajor = true   // true: [1, anchors, values]
    private var outputHasObjectness = false // true when values == classes + 5
    private var outputIsQuantized = false
    private var outputScale = 1f
    private var outputZeroPoint = 0

    private var labels: List<String> = emptyList()

    /** True when a model file was found and loaded successfully. */
    var isModelAvailable = false
        private set

    val classLabels: List<String> get() = labels

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Synchronized
    fun load() {
        if (isModelAvailable) return
        labels = loadLabels()
        try {
            val model = loadModelFile()
            val options = buildBestOptions()
            val interp = Interpreter(model, options)
            interpreter = interp
            configureFromTensors(interp)
            isModelAvailable = true
            AppLogger.i(
                TAG, "YOLO model loaded: input=$inputSize (${if (isNchw) "NCHW" else "NHWC"}), " +
                    "output anchors=$outputNumAnchors, quantized=$isQuantizedInput"
            )
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to load YOLO model '$modelFileName'", t)
            isModelAvailable = false
        }
    }

    override fun close() {
        synchronized(this) {
            interpreter?.close()
            interpreter = null
            delegate?.close()
            delegate = null
            inputBuffer = null
            isModelAvailable = false
        }
    }

    // ------------------------------------------------------------------
    // Inference
    // ------------------------------------------------------------------

    /**
     * Runs detection on [bitmap].
     *
     * @param confidenceThreshold minimum class score (default 0.45).
     * @param iouThreshold        NMS IoU threshold (default 0.5).
     * @return detections with normalized boxes + class indices.
     */
    @Synchronized
    fun detect(
        bitmap: Bitmap,
        confidenceThreshold: Float = Constants.DEFAULT_CONFIDENCE_THRESHOLD,
        iouThreshold: Float = Constants.DEFAULT_IOU_THRESHOLD
    ): List<RawDetection> {
        val interp = interpreter
        if (!isModelAvailable || interp == null || labels.isEmpty()) return emptyList()
        return try {
            preprocess(bitmap)
            val output = runInference(interp)
            postprocess(output, confidenceThreshold, iouThreshold)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Inference failed", t)
            emptyList()
        }
    }

    // ------------------------------------------------------------------
    // Preprocessing (spec 3.2b)
    // ------------------------------------------------------------------

    private fun preprocess(bitmap: Bitmap) {
        // 1. Stretch-resize to the square model input.
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        if (scaled !== bitmap) scaled.recycle()

        val buffer = requireNotNull(inputBuffer) { "Model not configured" }
        buffer.rewind()
        val floatInput = !isQuantizedInput

        if (floatInput) {
            // Normalize to [0, 1]; layout either NHWC or NCHW.
            if (isNchw) {
                writeChannelNchw(buffer, pixels)
            } else {
                for (pixel in pixels) {
                    buffer.putFloat(((pixel shr 16 and 0xFF) / 255f))
                    buffer.putFloat(((pixel shr 8 and 0xFF) / 255f))
                    buffer.putFloat(((pixel and 0xFF) / 255f))
                }
            }
        } else {
            // Quantized (uint8) input: q = round(real / scale) + zeroPoint,
            // where real = pixel/255. With Ultralytics' default (scale=1/255,
            // zp=0) this reduces to writing the raw pixel value.
            if (isNchw) {
                writeChannelNchwUint8(buffer, pixels)
            } else {
                for (pixel in pixels) {
                    buffer.put(quantize((pixel shr 16 and 0xFF) / 255f))
                    buffer.put(quantize((pixel shr 8 and 0xFF) / 255f))
                    buffer.put(quantize((pixel and 0xFF) / 255f))
                }
            }
        }
        buffer.rewind()
    }

    private fun writeChannelNchw(buffer: ByteBuffer, pixels: IntArray) {
        // R plane.
        for (pixel in pixels) buffer.putFloat(((pixel shr 16 and 0xFF) / 255f))
        // G plane.
        for (pixel in pixels) buffer.putFloat(((pixel shr 8 and 0xFF) / 255f))
        // B plane.
        for (pixel in pixels) buffer.putFloat(((pixel and 0xFF) / 255f))
    }

    private fun writeChannelNchwUint8(buffer: ByteBuffer, pixels: IntArray) {
        for (pixel in pixels) buffer.put(quantize((pixel shr 16 and 0xFF) / 255f))
        for (pixel in pixels) buffer.put(quantize((pixel shr 8 and 0xFF) / 255f))
        for (pixel in pixels) buffer.put(quantize((pixel and 0xFF) / 255f))
    }

    /** real [0,1] -> uint8 quantized byte. */
    private fun quantize(real: Float): Byte {
        val q = (real / inputScale).roundToInt() + inputZeroPoint
        return q.coerceIn(0, 255).toByte()
    }

    // ------------------------------------------------------------------
    // Inference execution
    // ------------------------------------------------------------------

    private fun runInference(interp: Interpreter): Any {
        val buffer = inputBuffer!!
        // Allocate a matching output container based on tensor metadata.
        val output: Any = when {
            !outputIsQuantized && outputAnchorsMajor ->
                Array(1) { Array(outputNumAnchors) { FloatArray(outputNumValues) } }
            !outputIsQuantized && !outputAnchorsMajor ->
                Array(1) { Array(outputNumValues) { FloatArray(outputNumAnchors) } }
            outputIsQuantized && outputAnchorsMajor ->
                Array(1) { Array(outputNumAnchors) { ByteArray(outputNumValues) } }
            else ->
                Array(1) { Array(outputNumValues) { ByteArray(outputNumAnchors) } }
        }
        interp.run(buffer, output)
        return output
    }

    // ------------------------------------------------------------------
    // Post-processing (spec 3.2c): threshold filter + NMS
    // ------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun postprocess(
        output: Any,
        confidenceThreshold: Float,
        iouThreshold: Float
    ): List<RawDetection> {
        val numClasses = labels.size
        val scoreOffset = if (outputHasObjectness) 5 else 4

        data class Candidate(val det: RawDetection, val score: Float, val classId: Int)

        val candidates = ArrayList<Candidate>(64)

        for (anchor in 0 until outputNumAnchors) {
            // Find the best class for this anchor.
            var bestClass = -1
            var bestScore = 0f
            for (cls in 0 until numClasses) {
                val score = readValue(output, anchor, scoreOffset + cls)
                if (score > bestScore) {
                    bestScore = score
                    bestClass = cls
                }
            }
            if (bestClass < 0 || bestScore < confidenceThreshold) continue

            // Optional objectness (YOLOv5-style exports).
            val objectness = if (outputHasObjectness) readValue(output, anchor, 4) else 1f
            val finalScore = bestScore * objectness
            if (finalScore < confidenceThreshold) continue

            val cx = readValue(output, anchor, 0)
            val cy = readValue(output, anchor, 1)
            val w = readValue(output, anchor, 2)
            val h = readValue(output, anchor, 3)

            candidates.add(
                Candidate(
                    RawDetection(cx, cy, w, h, bestClass, finalScore),
                    finalScore,
                    bestClass
                )
            )
        }

        // Per-class non-maximum suppression.
        val kept = mutableListOf<RawDetection>()
        candidates
            .groupBy { it.classId }
            .forEach { (_, classCandidates) ->
                val sorted = classCandidates.sortedByDescending { it.score }
                val suppressed = BooleanArray(sorted.size)
                for (i in sorted.indices) {
                    if (suppressed[i]) continue
                    kept.add(sorted[i].det)
                    for (j in i + 1 until sorted.size) {
                        if (suppressed[j]) continue
                        if (iou(sorted[i].det, sorted[j].det) > iouThreshold) {
                            suppressed[j] = true
                        }
                    }
                }
            }
        return kept
    }

    /** Reads output[0][a][v] or output[0][v][a] depending on layout, dequantizing if needed. */
    private fun readValue(output: Any, anchor: Int, valueIndex: Int): Float {
        return if (!outputIsQuantized) {
            if (outputAnchorsMajor) {
                val arr = output as Array<Array<FloatArray>>
                arr[0][anchor][valueIndex]
            } else {
                val arr = output as Array<Array<FloatArray>>
                arr[0][valueIndex][anchor]
            }
        } else {
            val raw: Byte = if (outputAnchorsMajor) {
                val arr = output as Array<Array<ByteArray>>
                arr[0][anchor][valueIndex]
            } else {
                val arr = output as Array<Array<ByteArray>>
                arr[0][valueIndex][anchor]
            }
            val unsigned = raw.toInt() and 0xFF
            (unsigned - outputZeroPoint) * outputScale
        }
    }

    private fun iou(a: RawDetection, b: RawDetection): Float {
        val aX1 = a.cx - a.width / 2f
        val aY1 = a.cy - a.height / 2f
        val aX2 = a.cx + a.width / 2f
        val aY2 = a.cy + a.height / 2f
        val bX1 = b.cx - b.width / 2f
        val bY1 = b.cy - b.height / 2f
        val bX2 = b.cx + b.width / 2f
        val bY2 = b.cy + b.height / 2f
        val interW = max(0f, min(aX2, bX2) - max(aX1, bX1))
        val interH = max(0f, min(aY2, bY2) - max(aY1, bY1))
        val inter = interW * interH
        val union = a.width * a.height + b.width * b.height - inter
        return if (union <= 0f) 0f else inter / union
    }

    // ------------------------------------------------------------------
    // Setup helpers
    // ------------------------------------------------------------------

    private fun configureFromTensors(interp: Interpreter) {
        val input = interp.getInputTensor(0)
        val shape = input.shape() // e.g. [1, 320, 320, 3] or [1, 3, 320, 320]
        require(shape.size == 4) { "Unexpected input rank: ${shape.joinToString()}" }
        inputSize = if (shape[1] == 3) shape[2] else shape[1]
        isNchw = shape[1] == 3

        isQuantizedInput = input.dataType() == DataType.UINT8 || input.dataType() == DataType.INT8
        val q = input.quantizationParams()
        inputScale = q.scale
        inputZeroPoint = q.zeroPoint

        val elementBytes = if (isQuantizedInput) 1 else 4
        inputBuffer = ByteBuffer
            .allocateDirect(1 * inputSize * inputSize * 3 * elementBytes)
            .order(ByteOrder.nativeOrder())

        val output = interp.getOutputTensor(0)
        val outShape = output.shape()
        require(outShape.size == 3) { "Unexpected output rank: ${outShape.joinToString()}" }
        val numClasses = max(1, labels.size)
        val valuesNoObj = numClasses + 4       // 84 for COCO
        val valuesWithObj = numClasses + 5     // 85 for COCO (YOLOv5 style)

        when {
            outShape[1] == valuesNoObj || outShape[1] == valuesWithObj -> {
                // [1, values, anchors] — Ultralytics ONNX/TFLite layout.
                outputAnchorsMajor = false
                outputNumValues = outShape[1]
                outputNumAnchors = outShape[2]
            }
            outShape[2] == valuesNoObj || outShape[2] == valuesWithObj -> {
                // [1, anchors, values]
                outputAnchorsMajor = true
                outputNumAnchors = outShape[1]
                outputNumValues = outShape[2]
            }
            else -> {
                // Last resort: assume [1, anchors, values] with an unknown head.
                AppLogger.w(TAG, "Unrecognized output shape ${outShape.joinToString()}; guessing anchors-major")
                outputAnchorsMajor = true
                outputNumAnchors = outShape[1]
                outputNumValues = outShape[2]
            }
        }
        outputHasObjectness = outputNumValues == valuesWithObj
        outputIsQuantized =
            output.dataType() == DataType.UINT8 || output.dataType() == DataType.INT8
        val oq = output.quantizationParams()
        outputScale = oq.scale
        outputZeroPoint = oq.zeroPoint
    }

    /** GPU -> NNAPI -> CPU delegate chain (spec 3.2). */
    private fun buildBestOptions(): Interpreter.Options {
        // 1. GPU delegate (best speed when compatible).
        try {
            val compatibility = CompatibilityList()
            if (compatibility.isDelegateSupportedOnThisDevice) {
                val gpu = GpuDelegate(compatibility.bestOptionsForThisDevice)
                delegate = gpu
                return Interpreter.Options()
                    .addDelegate(gpu)
                    .setNumThreads(NUM_THREADS)
            }
        } catch (t: Throwable) {
            AppLogger.w(TAG, "GPU delegate unavailable: ${t.message}")
        }
        // 2. NNAPI delegate.
        try {
            val nnapi = NnapiDelegate()
            delegate = nnapi
            return Interpreter.Options().addDelegate(nnapi).setNumThreads(NUM_THREADS)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "NNAPI delegate unavailable: ${t.message}")
        }
        // 3. Plain CPU (XNNPACK + 4 threads).
        return Interpreter.Options()
            .setUseXNNPACK(true)
            .setNumThreads(NUM_THREADS)
    }

    private fun loadModelFile(): MappedByteBuffer =
        context.assets.openFd(modelFileName).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
        }

    private fun loadLabels(): List<String> = try {
        context.assets.open(Constants.COCO_LABELS_FILE).bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    } catch (t: Throwable) {
        AppLogger.e(TAG, "Could not load COCO labels", t)
        emptyList()
    }

    /** Maps a class index to a label, tolerating mismatched label files. */
    fun labelFor(classId: Int): String =
        labels.getOrNull(classId) ?: "object"

    private companion object {
        const val TAG = "YoloV11Processor"
        const val NUM_THREADS = 4
    }
}
