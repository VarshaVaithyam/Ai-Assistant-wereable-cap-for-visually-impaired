# YOLO11n Training & Export Pipeline

Independent tooling used to produce `assets/models/yolo11n.tflite`, the
edge-optimized object-detection model the VisionAssist Android app runs
on-device. This pipeline is Python-based and is **not** part of the
Flutter build — run it separately whenever you need a new or re-trained
model.

## 1. Set up the environment

```bash
cd tools/yolo_training
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

## 2. Quick start: export the pretrained model

If you just want the stock COCO-trained YOLO11n running on the smart cap,
download `yolo11n.pt` (Ultralytics will fetch it automatically on first
use) and export it directly:

```bash
python export_to_tflite.py --weights yolo11n.pt --imgsz 320
```

This will:

1. Load `yolo11n.pt` with Ultralytics.
2. Export to ONNX with a **static** input shape `[1, 3, 320, 320]`
   (dynamic shapes are avoided because `tflite_flutter` requires a fixed
   input signature on-device).
3. Convert the ONNX graph to TensorFlow Lite via `onnx2tf`.
4. Copy the result to `assets/models/yolo11n.tflite`, ready for
   `flutter build apk --release` to bundle.

The output tensor is `[1, 84, 2100]`: 4 box attributes (`cx, cy, w, h`) +
80 COCO class scores, for 2100 candidate anchors at the 320x320 input
resolution. The app's `YoloDetectorService` decodes this layout directly.

## 3. INT8 post-training quantization

For faster, smaller on-device inference, quantize during export:

```bash
python export_to_tflite.py \
  --weights yolo11n.pt \
  --imgsz 320 \
  --int8 \
  --data dataset.yaml
```

`--data` should point at a dataset YAML (see `dataset.example.yaml`) whose
training images are used as the representative calibration set.

If you already have an intermediate SavedModel (e.g. from a previous
`onnx2tf` run) and only want to re-quantize, use the standalone helper:

```bash
python quantize_int8.py \
  --model export_work/tflite_out/<saved_model_dir> \
  --calib-images ./calibration_images \
  --imgsz 320 \
  --output ../../assets/models/yolo11n.tflite
```

## 4. Custom-dataset training

To fine-tune YOLO11n on additional hazard classes (stairs, curbs, open
doors, low-hanging obstacles, etc.), copy `dataset.example.yaml` to
`dataset.yaml`, point it at your labeled images in YOLO txt format, then:

```bash
python train.py \
  --data dataset.yaml \
  --base-weights yolo11n.pt \
  --epochs 100 \
  --batch 16 \
  --imgsz 320
```

Training writes checkpoints under `runs/train/<name>/weights/`. Export the
best checkpoint the same way as the pretrained model:

```bash
python export_to_tflite.py \
  --weights runs/train/visionassist_yolo11n/weights/best.pt \
  --imgsz 320
```

> **Important:** if you train on a custom class list, update
> `assets/models/labels.txt` in the Flutter app so detected class indices
> still map to the correct label strings.

## Files

| File | Purpose |
|---|---|
| `requirements.txt` | Python dependencies (Ultralytics, ONNX, onnx2tf, TensorFlow). |
| `export_to_tflite.py` | `.pt` → ONNX (static 320x320) → TFLite → `assets/models/`. |
| `train.py` | Fine-tunes YOLO11n on a custom dataset YAML. |
| `quantize_int8.py` | Standalone INT8 quantization from a SavedModel + calibration images. |
| `dataset.example.yaml` | Template YOLO dataset definition for custom training. |
