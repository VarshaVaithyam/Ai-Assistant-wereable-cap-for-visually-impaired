import 'dart:io';
import 'dart:typed_data';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:path_provider/path_provider.dart';
import '../models/ocr_result.dart';

class OcrService {
  /// ML Kit Latin recognizer runs locally. Does not claim Tamil OCR support.
  Future<OcrResult> readBytes(Uint8List bytes) async {
    final parent = await getTemporaryDirectory();
    final directory = await parent.createTemp('visionassist-ocr-');
    final file = File('${directory.path}/capture.jpg');
    final recognizer = TextRecognizer(script: TextRecognitionScript.latin);
    try {
      await file.writeAsBytes(bytes, flush: true);
      final recognized = await recognizer.processImage(InputImage.fromFilePath(file.path));
      final lines = recognized.blocks.expand((block) => block.lines)
        .map((line) => line.text.trim()).where((line) => line.isNotEmpty).toList();
      return OcrResult(text: recognized.text.trim(), lines: lines,
        candidates: rankSignText(lines));
    } finally {
      try {
        await recognizer.close();
      } finally {
        if (await directory.exists()) await directory.delete(recursive: true);
      }
    }
  }
}
