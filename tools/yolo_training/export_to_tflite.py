#!/usr/bin/env python3
"""Export a trained/pretrained YOLO11n model to the TensorFlow Lite format
consumed by the VisionAssist Android app (assets/models/yolo11n.tflite).

Pipeline:
    1. Load the Ultralytics YOLO11n checkpoint (.pt).
    2. Export to ONNX with a static input shape [1, 3, imgsz, imgsz].
    3. Convert ONNX -> TensorFlow SavedModel -> TensorFlow Lite via onnx2tf.
    4. Optionally apply INT8 post-training quantization using a calibration
       dataset.
    5. Copy the resulting .tflite file into assets/models/.

Usage:
    python export_to_tflite.py --weights yolo11n.pt --imgsz 320
    python export_to_tflite.py --weights runs/train/exp/weights/best.pt \
        --imgsz 320 --int8 --data dataset.yaml
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUTPUT = REPO_ROOT / "assets" / "models" / "yolo11n.tflite"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--weights",
        type=str,
        default="yolo11n.pt",
        help="Path to the Ultralytics YOLO11n .pt checkpoint "
        "(pretrained or fine-tuned).",
    )
    parser.add_argument(
        "--imgsz",
        type=int,
        default=320,
        help="Static square input size fed to the model (default: 320, "
        "matching the app's letterboxed 320x320 preprocessing).",
    )
    parser.add_argument(
        "--int8",
        action="store_true",
        help="Apply INT8 post-training quantization during the TFLite "
        "conversion step (requires --data for calibration images).",
    )
    parser.add_argument(
        "--data",
        type=str,
        default=None,
        help="Dataset YAML used for INT8 calibration images when --int8 "
        "is set.",
    )
    parser.add_argument(
        "--output",
        type=str,
        default=str(DEFAULT_OUTPUT),
        help="Destination path for the final .tflite file.",
    )
    parser.add_argument(
        "--workdir",
        type=str,
        default="export_work",
        help="Scratch directory for intermediate ONNX / SavedModel files.",
    )
    return parser.parse_args()


def export_to_onnx(weights: str, imgsz: int, workdir: Path) -> Path:
    """Exports the YOLO11n checkpoint to ONNX with a static input shape."""
    from ultralytics import YOLO

    print(f"[1/4] Loading YOLO11n checkpoint: {weights}")
    model = YOLO(weights)

    print(f"[2/4] Exporting to ONNX (static {imgsz}x{imgsz} input)...")
    onnx_path = model.export(
        format="onnx",
        imgsz=imgsz,
        dynamic=False,  # static shape: [1, 3, imgsz, imgsz]
        simplify=True,
        opset=12,
    )
    onnx_path = Path(onnx_path)
    workdir.mkdir(parents=True, exist_ok=True)
    destination = workdir / onnx_path.name
    if onnx_path.resolve() != destination.resolve():
        shutil.copy(onnx_path, destination)
    print(f"      ONNX model written to {destination}")
    return destination


def convert_onnx_to_tflite(
    onnx_path: Path, workdir: Path, int8: bool, data_yaml: str | None
) -> Path:
    """Runs onnx2tf to produce a TensorFlow Lite model from the ONNX graph."""
    print("[3/4] Converting ONNX -> TensorFlow SavedModel -> TFLite "
          "(onnx2tf)...")
    output_dir = workdir / "tflite_out"
    output_dir.mkdir(parents=True, exist_ok=True)

    cmd = [
        sys.executable,
        "-m",
        "onnx2tf",
        "-i",
        str(onnx_path),
        "-o",
        str(output_dir),
        "-osd",  # output signaturedefs for easier interpreter binding
    ]
    if int8:
        if not data_yaml:
            raise ValueError("--int8 requires --data <dataset.yaml> for "
                              "calibration images")
        cmd += ["-oiqt", "-qt", "per-tensor"]

    subprocess.run(cmd, check=True)

    candidates = sorted(output_dir.glob("*.tflite"))
    if not candidates:
        raise FileNotFoundError(
            f"onnx2tf did not produce a .tflite file in {output_dir}"
        )

    # Prefer the (optionally) quantized integer model if present, else the
    # first float model produced.
    preferred = [c for c in candidates if "integer_quant" in c.name]
    chosen = preferred[0] if preferred else candidates[0]
    print(f"      TFLite model selected: {chosen}")
    return chosen


def install_to_assets(tflite_path: Path, output: Path) -> None:
    print(f"[4/4] Copying model to {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy(tflite_path, output)
    print("Done. Update assets/models/labels.txt if you trained on a "
          "custom class set.")


def main() -> None:
    args = parse_args()
    workdir = Path(args.workdir)

    onnx_path = export_to_onnx(args.weights, args.imgsz, workdir)
    tflite_path = convert_onnx_to_tflite(onnx_path, workdir, args.int8, args.data)
    install_to_assets(tflite_path, Path(args.output))


if __name__ == "__main__":
    main()
