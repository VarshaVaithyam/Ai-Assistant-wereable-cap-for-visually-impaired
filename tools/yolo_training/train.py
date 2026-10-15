#!/usr/bin/env python3
"""Fine-tunes YOLO11n on a custom obstacle/hazard dataset for VisionAssist.

Supports a configurable dataset YAML, epoch count, batch size, and input
size, then hands off to export_to_tflite.py for the ONNX -> TFLite
conversion. Run `python train.py --help` for all options.

Example:
    python train.py --data dataset.yaml --epochs 100 --batch 16 --imgsz 320
"""
from __future__ import annotations

import argparse
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--data",
        type=str,
        required=True,
        help="Path to a YOLO-format dataset YAML (train/val image dirs + "
        "class names). See dataset.example.yaml for the expected layout.",
    )
    parser.add_argument(
        "--base-weights",
        type=str,
        default="yolo11n.pt",
        help="Pretrained YOLO11n checkpoint to fine-tune from.",
    )
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--batch", type=int, default=16)
    parser.add_argument(
        "--imgsz",
        type=int,
        default=320,
        help="Training/inference resolution; keep this matched to the "
        "app's 320x320 letterboxed input.",
    )
    parser.add_argument(
        "--project",
        type=str,
        default="runs/train",
        help="Directory where training runs are written.",
    )
    parser.add_argument("--name", type=str, default="visionassist_yolo11n")
    parser.add_argument(
        "--device",
        type=str,
        default=None,
        help="Training device, e.g. '0' for first GPU or 'cpu'. Defaults "
        "to Ultralytics auto-detection.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    from ultralytics import YOLO

    model = YOLO(args.base_weights)
    results = model.train(
        data=args.data,
        epochs=args.epochs,
        batch=args.batch,
        imgsz=args.imgsz,
        project=args.project,
        name=args.name,
        device=args.device,
    )

    best_weights = Path(args.project) / args.name / "weights" / "best.pt"
    print("\nTraining complete.")
    print(f"Best weights: {best_weights}")
    print(
        "Next step:\n"
        f"  python export_to_tflite.py --weights {best_weights} "
        f"--imgsz {args.imgsz}"
    )
    _ = results  # Ultralytics also writes metrics/plots under --project.


if __name__ == "__main__":
    main()
