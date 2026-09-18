# ML models folder

Place your exported YOLOv11n TFLite model here before building:

```
app/src/main/assets/models/
├── coco_labels.txt                    <- included (80 COCO classes)
├── yolo11n_float16.tflite             <- RECOMMENDED (add this)
└── yolo11n_full_integer_quant.tflite  <- optional int8 alternative
```

## How to export the model

Quick version (Python 3.8+):

```bash
pip install ultralytics
yolo export model=yolo11n.pt format=tflite imgsz=320
```

Or use the helper script from the repository root:

```bash
python tools/export_yolo_model.py --model yolo11n.pt --imgsz 320
```

The export writes the `.tflite` files into this folder automatically.
Full instructions (including int8 quantization and custom training):
see [docs/MODEL_PREPARATION.md](../../../docs/MODEL_PREPARATION.md).

## Why float16 is recommended

The **float16** export keeps `float32` input/output tensors, so the app's
pre/post-processing is a straight normalization + box decode. The **full
integer (int8)** export also works — `YoloV11Processor` reads the tensor
quantization parameters and dequantizes automatically — but it needs a
representative dataset at export time and is more sensitive to accuracy loss.

The `.tflite` files are excluded from git (`.gitignore`) — they are large
binaries; each developer builds their own.
