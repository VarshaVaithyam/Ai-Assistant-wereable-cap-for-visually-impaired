/// A piece of recognized text from a signboard image, scored by heuristics
/// so the most likely destination names float to the top of the list.
class OcrCandidate {
  const OcrCandidate({
    required this.text,
    required this.score,
    required this.confidence,
  });

  final String text;

  /// Heuristic signboard-relevance score (higher = more likely to be a
  /// meaningful destination / place name rather than noise).
  final double score;

  /// Average per-character recognition confidence reported by ML Kit
  /// (0-1), when available.
  final double confidence;

  OcrCandidate copyWith({double? score}) => OcrCandidate(
        text: text,
        score: score ?? this.score,
        confidence: confidence,
      );
}
