#!/usr/bin/env python3
"""
VisionAid — YOLOv11n TFLite export helper (Step 3.1 of the project spec).

Exports an Ultralytics YOLOv11n model to TensorFlow Lite and copies the
resulting files into the Android app's assets folder.

Usage:
    # Recommended: float16 (float32 I/O, easiest to consume on-device)
    python tools/export_yolo_model.py --model yolo11n.pt --imgsz 320

    # Fully-quantized int8 (smaller + faster, needs a calibration dataset
    # built from your own images)
    python tools/export_yolo_model.py --model yolo11n.pt --imgsz 320 --int8

    # Export a custom-trained model
    python tools/export_yolo_model.py --model runs/detect/train/weights/best.pt

Requirements:
    pip install ultralytics   # pulls torch, onnx, tensorflow, etc.
"""

import argparse
import shutil
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
ASSETS_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "models"

# Files produced by `yolo export format=tflite` that are useful to us.
CANDIDATES = [
    "yolo11n_float16.tflite",
    "yolo11n_full_integer_quant.tflite",
]


def main() -> None:
    parser = argparse.ArgumentParser(description="Export YOLOv11n to TFLite for VisionAid")
    parser.add_argument("--model", default="yolo11n.pt",
                        help="Path/ID of the model to export (default: yolo11n.pt)")
    parser.add_argument("--imgsz", type=int, default=320,
                        help="Input size (default: 320 — matches QVGA camera + fast on mobile)")
    parser.add_argument("--int8", action="store_true",
                        help="Also run the full-int8 export (needs calibration data)")
    parser.add_argument("--no-copy", action="store_true",
                        help="Do not copy results into app assets")
    args = parser.parse_args()

    try:
        from ultralytics import YOLO  # noqa: E402
    except ImportError:
        raise SystemExit(
            "Ultralytics is not installed. Run:\n    pip install ultralytics"
        )

    ASSETS_DIR.mkdir(parents=True, exist_ok=True)

    model = YOLO(args.model)
    print(f"Exporting {args.model} (imgsz={args.imgsz}, int8={args.int8}) ...")

    # Float16 export — recommended for this app.
    export_path = model.export(format="tflite", imgsz=args.imgsz, int8=False)
    print(f"Float16 export done: {export_path}")

    int8_path = None
    if args.int8:
        int8_path = model.export(format="tflite", imgsz=args.imgsz, int8=True)
        print(f"Int8 export done: {int8_path}")

    if args.no_copy:
        return

    # Ultralytics writes next to the source weights; find + copy artifacts.
    copied = []
    for candidate in CANDIDATES:
        source = Path(export_path).parent / candidate
        if source.exists():
            shutil.copy2(source, ASSETS_DIR / candidate)
            copied.append(candidate)
    # If the export produced a differently-named single file, copy it anyway.
    if not copied and Path(export_path).suffix == ".tflite":
        target = ASSETS_DIR / "yolo11n_float16.tflite"
        shutil.copy2(export_path, target)
        copied.append(target.name)

    print("\nCopied into", ASSETS_DIR)
    for name in copied:
        print("  -", name)
    print(
        "\nNext steps:\n"
        "  1. Build the app in Android Studio (the model is loaded from assets\n"
        "     at startup; see ml/YoloV11Processor.kt).\n"
        "  2. Verify detections on the Detection screen.\n"
        "  Detailed guide: docs/MODEL_PREPARATION.md"
    )


if __name__ == "__main__":
    main()
