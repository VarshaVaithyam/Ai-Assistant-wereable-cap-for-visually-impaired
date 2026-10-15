import 'dart:io';
import 'dart:typed_data';

import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:path_provider/path_provider.dart';

import '../models/ocr_candidate.dart';

/// On-device OCR for signboards, street names, transit stations, and
/// building plaques, using Google ML Kit Text Recognition.
///
/// Recognized text blocks are ranked with a heuristic "signboard score" so
/// the most plausible destination names surface first.
class OcrService {
  final TextRecognizer _recognizer =
      TextRecognizer(script: TextRecognitionScript.latin);

  /// Runs OCR on JPEG [imageBytes] captured from the smart-cap `/capture`
  /// endpoint (or the phone camera) and returns ranked destination
  /// candidates.
  Future<List<OcrCandidate>> recognize(Uint8List imageBytes) async {
    final File tempFile = await _writeTempJpeg(imageBytes);
    try {
      final InputImage inputImage = InputImage.fromFilePath(tempFile.path);
      final RecognizedText result = await _recognizer.processImage(inputImage);
      return _rankCandidates(result);
    } finally {
      if (await tempFile.exists()) {
        await tempFile.delete();
      }
    }
  }

  Future<File> _writeTempJpeg(Uint8List bytes) async {
    final Directory dir = await getTemporaryDirectory();
    final File file = File(
      '${dir.path}/ocr_capture_${DateTime.now().microsecondsSinceEpoch}.jpg',
    );
    return file.writeAsBytes(bytes, flush: true);
  }

  List<OcrCandidate> _rankCandidates(RecognizedText result) {
    final List<OcrCandidate> candidates = <OcrCandidate>[];

    for (final TextBlock block in result.blocks) {
      final String text = block.text.trim();
      if (text.isEmpty) continue;

      final double score = _signboardScore(block);
      candidates.add(OcrCandidate(
        text: text,
        score: score,
        confidence: _averageConfidence(block),
      ));
    }

    candidates.sort((OcrCandidate a, OcrCandidate b) => b.score.compareTo(a.score));
    return candidates;
  }

  /// Heuristic scoring favouring text that looks like a destination name
  /// or signboard rather than incidental background text:
  ///  * Title-cased / all-caps words score higher (typical of signage).
  ///  * Larger bounding boxes (closer to camera / more prominent) score
  ///    higher.
  ///  * Very short (<=2 chars) or very long (>60 chars, likely paragraphs)
  ///    text is penalized.
  ///  * Strings containing digits-only or excessive punctuation are
  ///    penalized (unlikely to be a place name).
  double _signboardScore(TextBlock block) {
    final String text = block.text.trim();
    double score = 0;

    final int length = text.length;
    if (length <= 2) {
      score -= 5;
    } else if (length > 60) {
      score -= 3;
    } else {
      score += 2;
    }

    final bool isAllCaps = text == text.toUpperCase() && text != text.toLowerCase();
    final bool isTitleCase = RegExp(r'^([A-Z][a-z]*\s?)+$').hasMatch(text);
    if (isAllCaps || isTitleCase) score += 3;

    final bool looksNumeric = RegExp(r'^[\d\s.,:-]+$').hasMatch(text);
    if (looksNumeric) score -= 4;

    final int wordCount = text.split(RegExp(r'\s+')).length;
    if (wordCount.in1to4) score += 2;

    final double area = (block.boundingBox.width * block.boundingBox.height)
        .toDouble();
    score += (area / 10000).clamp(0, 5);

    final List<String> keywords = <String>[
      'station',
      'hospital',
      'bus',
      'stop',
      'road',
      'street',
      'market',
      'school',
      'college',
      'mall',
      'temple',
      'park',
    ];
    final String lower = text.toLowerCase();
    for (final String keyword in keywords) {
      if (lower.contains(keyword)) {
        score += 3;
        break;
      }
    }

    return score;
  }

  double _averageConfidence(TextBlock block) {
    final List<double> confidences = <double>[];
    for (final TextLine line in block.lines) {
      for (final TextElement element in line.elements) {
        final double? conf = element.confidence;
        if (conf != null) confidences.add(conf);
      }
    }
    if (confidences.isEmpty) return 1.0;
    return confidences.reduce((double a, double b) => a + b) / confidences.length;
  }

  Future<void> dispose() async {
    await _recognizer.close();
  }
}

extension on int {
  bool get in1to4 => this >= 1 && this <= 4;
}
