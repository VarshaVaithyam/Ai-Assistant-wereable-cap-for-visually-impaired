import 'dart:typed_data';

/// Incremental JPEG extractor for multipart MJPEG. Handles arbitrary HTTP chunk
/// boundaries, drops oversized/corrupt parts, and never buffers an entire stream.
class MjpegDecoder {
  MjpegDecoder({this.maxFrameBytes = 2 * 1024 * 1024});
  final int maxFrameBytes;
  List<int> _frame = [];
  int _previous = -1;
  bool _inside = false;

  List<Uint8List> add(List<int> bytes) {
    final frames = <Uint8List>[];
    for (final byte in bytes) {
      if (!_inside) {
        if (_previous == 0xff && byte == 0xd8) {
          _inside = true;
          _frame = [0xff, 0xd8];
        }
      } else {
        _frame.add(byte);
        if (_frame.length > maxFrameBytes) {
          _frame = [];
          _inside = false;
        } else if (_previous == 0xff && byte == 0xd9) {
          frames.add(Uint8List.fromList(_frame));
          _frame = [];
          _inside = false;
        }
      }
      _previous = byte;
    }
    return frames;
  }
}
