import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui';

import 'package:flutter/services.dart' show rootBundle;
import 'package:image/image.dart' as img;
import 'package:tflite_flutter/tflite_flutter.dart';

import '../core/constants/app_constants.dart';
import '../models/detection.dart';

/// Result of letterboxing a source frame to the model's square input size,
/// kept so detection boxes can be mapped back to the original image.
class _LetterboxInfo {
  _LetterboxInfo({
    required this.scale,
    required this.padX,
    required this.padY,
    required this.srcWidth,
    required this.srcHeight,
  });

  final double scale;
  final double padX;
  final double padY;
  final int srcWidth;
  final int srcHeight;
}

/// Loads the edge-optimized YOLO11n TensorFlow Lite model and runs
/// real-time object detection on individual camera frames.
///
/// Pipeline: JPEG decode -> aspect-preserving letterbox to 320x320 ->
/// Float32 normalization -> TFLite inference -> decode [1, 84, 2100]
/// output -> confidence filter -> class-aware NMS (IoU 0.45).
class YoloDetectorService {
  Interpreter? _interpreter;
  List<String> _labels = <String>[];
  bool get isLoaded => _interpreter != null;

  List<String> get labels => List<String>.unmodifiable(_labels);

  Future<void> loadModel({
    String modelAsset = AppConstants.yoloModelAsset,
    String labelsAsset = AppConstants.cocoLabelsAsset,
  }) async {
    final InterpreterOptions options = InterpreterOptions()..threads = 4;
    _interpreter =
        await Interpreter.fromAsset(modelAsset, options: options);
    final String labelData = await rootBundle.loadString(labelsAsset);
    _labels = labelData
        .split('\n')
        .map((String e) => e.trim())
        .where((String e) => e.isNotEmpty)
        .toList(growable: false);
  }

  /// Runs detection on a single JPEG-encoded [frame].
  ///
  /// Returns detections with boxes normalized to the *original* frame
  /// (letterbox padding already removed), suitable for direct overlay
  /// drawing regardless of the source resolution.
  List<Detection> detect(
    Uint8List frame, {
    double confidenceThreshold = AppConstants.defaultConfidenceThreshold,
    double iouThreshold = AppConstants.nmsIouThreshold,
  }) {
    final Interpreter? interpreter = _interpreter;
    if (interpreter == null) {
      throw StateError('YoloDetectorService.loadModel() must be called first');
    }

    final img.Image? decoded = img.decodeImage(frame);
    if (decoded == null) return const <Detection>[];

    final int size = AppConstants.modelInputSize;
    final _LetterboxInfo info = _LetterboxInfo(
      scale: math.min(size / decoded.width, size / decoded.height),
      padX: 0,
      padY: 0,
      srcWidth: decoded.width,
      srcHeight: decoded.height,
    );
    final double scaledW = decoded.width * info.scale;
    final double scaledH = decoded.height * info.scale;
    final double padX = (size - scaledW) / 2;
    final double padY = (size - scaledH) / 2;
    final _LetterboxInfo letterbox = _LetterboxInfo(
      scale: info.scale,
      padX: padX,
      padY: padY,
      srcWidth: decoded.width,
      srcHeight: decoded.height,
    );

    final Float32List inputBuffer = _letterboxToFloat32(decoded, size, letterbox);

    // Output tensor shape: [1, 84, 2100] -> 4 box attrs + 80 class scores,
    // for 2100 candidate anchors.
    final List<List<List<double>>> output = List<List<List<double>>>.generate(
      1,
      (_) => List<List<double>>.generate(
        AppConstants.numBoxAttributes,
        (_) => List<double>.filled(AppConstants.numPredictions, 0.0),
      ),
    );

    interpreter.run(
      inputBuffer.reshape(<int>[1, size, size, 3]),
      output,
    );

    final List<Detection> raw = _decodeOutput(
      output[0],
      letterbox,
      confidenceThreshold,
    );

    return _nonMaxSuppression(raw, iouThreshold);
  }

  Float32List _letterboxToFloat32(
    img.Image source,
    int size,
    _LetterboxInfo info,
  ) {
    final img.Image canvas = img.Image(width: size, height: size);
    img.fill(canvas, color: img.ColorRgb8(114, 114, 114));

    final int resizedW = (source.width * info.scale).round();
    final int resizedH = (source.height * info.scale).round();
    final img.Image resized = img.copyResize(
      source,
      width: resizedW,
      height: resizedH,
      interpolation: img.Interpolation.linear,
    );

    img.compositeImage(
      canvas,
      resized,
      dstX: info.padX.round(),
      dstY: info.padY.round(),
    );

    final Float32List floatData = Float32List(size * size * 3);
    int idx = 0;
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        final img.Pixel pixel = canvas.getPixel(x, y);
        floatData[idx++] = pixel.r / 255.0;
        floatData[idx++] = pixel.g / 255.0;
        floatData[idx++] = pixel.b / 255.0;
      }
    }
    return floatData;
  }

  List<Detection> _decodeOutput(
    List<List<double>> output,
    _LetterboxInfo info,
    double confidenceThreshold,
  ) {
    final List<Detection> detections = <Detection>[];
    final int size = AppConstants.modelInputSize;

    for (int i = 0; i < AppConstants.numPredictions; i++) {
      double bestScore = 0;
      int bestClass = -1;
      for (int c = 0; c < AppConstants.numClasses; c++) {
        final double score = output[4 + c][i];
        if (score > bestScore) {
          bestScore = score;
          bestClass = c;
        }
      }
      if (bestClass == -1 || bestScore < confidenceThreshold) continue;

      // Box attributes are center-x, center-y, width, height in the
      // model's 320x320 input space.
      final double cx = output[0][i];
      final double cy = output[1][i];
      final double w = output[2][i];
      final double h = output[3][i];

      double left = cx - w / 2;
      double top = cy - h / 2;
      double right = cx + w / 2;
      double bottom = cy + h / 2;

      // Undo letterbox padding + scale to map back into source image
      // space, then normalize to [0, 1].
      left = (left - info.padX) / info.scale;
      top = (top - info.padY) / info.scale;
      right = (right - info.padX) / info.scale;
      bottom = (bottom - info.padY) / info.scale;

      left = left.clamp(0, info.srcWidth.toDouble()).toDouble();
      top = top.clamp(0, info.srcHeight.toDouble()).toDouble();
      right = right.clamp(0, info.srcWidth.toDouble()).toDouble();
      bottom = bottom.clamp(0, info.srcHeight.toDouble()).toDouble();

      if (right <= left || bottom <= top) continue;

      final Rect normRect = Rect.fromLTRB(
        left / info.srcWidth,
        top / info.srcHeight,
        right / info.srcWidth,
        bottom / info.srcHeight,
      );

      final String label = bestClass < _labels.length
          ? _labels[bestClass]
          : 'class_$bestClass';

      detections.add(Detection(
        classId: bestClass,
        label: label,
        confidence: bestScore,
        rect: normRect,
      ));
    }
    // size is intentionally unused beyond documentation of the model's
    // fixed input resolution.
    assert(size == AppConstants.modelInputSize);
    return detections;
  }

  /// Class-aware greedy Non-Maximum Suppression.
  List<Detection> _nonMaxSuppression(
    List<Detection> detections,
    double iouThreshold,
  ) {
    final List<Detection> sorted = List<Detection>.of(detections)
      ..sort((Detection a, Detection b) => b.confidence.compareTo(a.confidence));

    final List<Detection> kept = <Detection>[];
    final List<bool> suppressed = List<bool>.filled(sorted.length, false);

    for (int i = 0; i < sorted.length; i++) {
      if (suppressed[i]) continue;
      final Detection a = sorted[i];
      kept.add(a);
      for (int j = i + 1; j < sorted.length; j++) {
        if (suppressed[j]) continue;
        final Detection b = sorted[j];
        if (a.classId != b.classId) continue;
        if (_iou(a.rect, b.rect) > iouThreshold) {
          suppressed[j] = true;
        }
      }
    }
    return kept;
  }

  double _iou(Rect a, Rect b) {
    final double interLeft = math.max(a.left, b.left);
    final double interTop = math.max(a.top, b.top);
    final double interRight = math.min(a.right, b.right);
    final double interBottom = math.min(a.bottom, b.bottom);

    final double interW = math.max(0.0, interRight - interLeft);
    final double interH = math.max(0.0, interBottom - interTop);
    final double interArea = interW * interH;

    final double unionArea =
        a.width * a.height + b.width * b.height - interArea;
    if (unionArea <= 0) return 0;
    return interArea / unionArea;
  }

  void dispose() {
    _interpreter?.close();
    _interpreter = null;
  }
}
