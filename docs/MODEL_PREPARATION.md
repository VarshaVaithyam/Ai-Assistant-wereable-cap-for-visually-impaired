# Model preparation — YOLOv11n for VisionAid

Object detection runs **fully on-device** with TensorFlow Lite. This guide
covers export, quantization choices and custom training.

## TL;DR

```bash
pip install ultralytics
python tools/export_yolo_model.py --model yolo11n.pt --imgsz 320
# -> app/src/main/assets/models/yolo11n_float16.tflite
```

Then rebuild the app. `ml/YoloV11Processor.kt` loads the model at startup and
enables detection automatically.

## Why these choices

| Setting | Value | Reason |
|---|---|---|
| Model | **yolo11n** | 6.5 MB fp16, ~39 GFLOPs — real-time on mid-range phones |
| Input size | **320×320** | matches QVGA (320×240) camera frames → minimal resize loss, faster than 640 |
| Format | **TFLite float16** | float32 I/O (simple pre/post-processing), half the size of fp32 |
| Alternative | **TFLite int8 (full integer)** | smaller/faster on CPU, but needs calibration data + the app must dequantize (supported) |

## What the export produces

`yolo export format=tflite` writes several files; the two we care about:

- `yolo11n_float16.tflite` — **recommended**, copy to `assets/models/`
- `yolo11n_full_integer_quant.tflite` — optional int8 variant

The app looks for `yolo11n_float16.tflite` first (see `Constants.YOLO_MODEL_FILE`);
change the constant if you use a different file name.

## How the app consumes the model

`YoloV11Processor` handles all the format details automatically:

- **Delegates:** GPU (via `CompatibilityList`) → NNAPI → CPU (XNNPACK, 4 threads).
- **Input layout:** both NHWC `[1,320,320,3]` and NCHW `[1,3,320,320]`.
- **Input dtype:** float32 (normalized 0–1) or uint8 (dequantized via the
  tensor's scale/zero-point — required for the int8 export).
- **Output layout:** both `[1,84,8400]` (Ultralytics values-major) and
  `[1,8400,84]` (anchors-major), with or without a YOLOv5-style objectness
  column (`[1,25200,85]`).
- **Post-processing:** confidence filter (default **0.45**) + per-class NMS
  (IoU **0.5**), exactly as specified in the project spec.

Labels come from `assets/models/coco_labels.txt` (80 classes, included).

## Custom training (recommended for real deployments)

Generic COCO classes miss many mobility-relevant objects (curbs, doors,
escalators, walking canes, guide dogs' harnesses). Fine-tuning on your own
images gives dramatically better guidance:

```python
from ultralytics import YOLO

# 1. Prepare a dataset in YOLO format:
#    dataset/images/train, dataset/images/val,
#    dataset/labels/train, dataset/labels/val, dataset/data.yaml
model = YOLO("yolo11n.pt")          # start from pretrained weights
model.train(data="dataset/data.yaml", epochs=60, imgsz=320)

# 2. Export the fine-tuned model
best = YOLO("runs/detect/train/weights/best.pt")
best.export(format="tflite", imgsz=320)   # float16
```

If your class set differs from COCO, also update `coco_labels.txt`
(one class per line, training order!) — the processor reads the label
count from that file to decode the output tensor.

## Int8 quantization specifics

Full-integer export uses a representative dataset for calibration:

```python
model.export(format="tflite", imgsz=320, int8=True,
             data="dataset/data.yaml")    # ultralytics auto-calibrates with val images
```

The app dequantizes int8/uint8 outputs using the tensor's quantization
parameters — no code changes needed. Do sanity-check accuracy after
quantization (int8 YOLO can lose a few mAP points on small objects).

## Verifying it works

1. Build + run the app, press **Start Detection**.
2. Open the **Detection** screen: you should see bounding boxes over the
   camera feed and spoken announcements through the earphones.
3. If the screen shows *"No YOLO model found"* the asset is missing — check
   `app/src/main/assets/models/` and that `noCompress "tflite"` stayed in
   `app/build.gradle`.

## ONNX alternative

The spec allows ONNX Runtime instead of TFLite — the dependency line is
commented in `app/build.gradle`. The cleanest swap point is
`YoloV11Processor`: keep the same `detect(Bitmap): List<RawDetection>`
interface and replace the interpreter internals.
