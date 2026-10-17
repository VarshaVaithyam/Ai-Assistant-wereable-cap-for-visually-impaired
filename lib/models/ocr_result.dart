/// Candidates are useful text snippets, never verified destinations.
class OcrResult {
  const OcrResult({required this.text, required this.lines, required this.candidates});
  final String text;
  final List<String> lines;
  final List<String> candidates;
}

List<String> rankSignText(Iterable<String> lines) {
  final unique = <String, String>{};
  for (final line in lines) {
    final text = line.replaceAll(RegExp(r'\s+'), ' ').trim();
    if (text.length < 3 || text.length > 120 || !RegExp(r'[a-zA-Z]').hasMatch(text)) continue;
    unique.putIfAbsent(text.toLowerCase(), () => text);
  }
  int score(String text) {
    final words = text.split(' ').length;
    final place = RegExp(r'\b(hospital|pharmacy|station|college|school|clinic|bus|road|street)\b', caseSensitive: false).hasMatch(text);
    return (place ? 100 : 0) + (words >= 2 && words <= 8 ? 20 : 0) - text.length ~/ 20;
  }
  final ranked = unique.values.toList()..sort((a, b) {
    final diff = score(b).compareTo(score(a));
    return diff != 0 ? diff : a.compareTo(b);
  });
  return ranked.take(6).toList();
}
