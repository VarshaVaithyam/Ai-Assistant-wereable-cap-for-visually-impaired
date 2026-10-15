# assets/models/

This directory is where the compiled `yolo11n.tflite` model must live before
you build the app — it is **not** checked into git because trained/exported
model weights are large binary artifacts.

| File | Required | Description |
|---|---|---|
| `labels.txt` | ✅ committed | COCO 80-class label list, one class per line, index-aligned with the model's output channels. |
| `yolo11n.tflite` | ❌ you generate it | Edge-optimized YOLO11n model, static input `[1, 3, 320, 320]`, output `[1, 84, 2100]`. |

## Getting `yolo11n.tflite`

Run the export pipeline in `tools/yolo_training/`:

```bash
cd tools/yolo_training
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python export_to_tflite.py --weights yolo11n.pt --imgsz 320
```

This exports the pretrained Ultralytics `yolo11n.pt` model to ONNX with a
static `320x320` input, converts it to TensorFlow Lite with `onnx2tf`, and
copies the result here as `assets/models/yolo11n.tflite`. See
`tools/yolo_training/README.md` for INT8 quantization and custom-dataset
training options.
