# Model artifact contract (not implemented in increment 1)

The future verified YOLO11n artifact belongs at `assets/models/yolo11n.tflite`.
There is intentionally no dummy model and the Flutter asset manifest does not
reference a nonexistent file. No runtime inference code exists yet.

Before integration, implement and test the separate Python pipeline under
`tools/yolo_training/` against an exact pinned dependency set. Obtain official
Ultralytics YOLO11n weights, export static 320x320 ONNX, convert using a tested
onnx2tf/TensorFlow combination, inspect real tensor layouts/dtypes/quantization,
and compare detections on real sample images. Do not assume [1,84,2100] without
inspection or treat a successful conversion as numerical equivalence. INT8
requires representative calibration data. Record artifact SHA-256, metadata,
conversion commands, dependency lock and validation output. Then declare the
actual model asset in pubspec.yaml.

Model licensing must be resolved before distribution; see THIRD_PARTY_NOTICES.md.
