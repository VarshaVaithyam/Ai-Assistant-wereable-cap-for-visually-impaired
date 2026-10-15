#!/usr/bin/env python3
"""Standalone INT8 post-training quantization helper.

Re-quantizes an existing float32 TFLite model using a folder of
representative calibration images, independent of the full export
pipeline in export_to_tflite.py. Useful when you already have a float
TFLite model and only want to shrink / speed it up for the ESP32-class
inference loop running on the phone.

Usage:
    python quantize_int8.py --model yolo11n_float32.tflite \
        --calib-images ./calibration_images --imgsz 320 \
        --output ../../assets/models/yolo11n.tflite
"""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, help="Float32 .tflite model path")
    parser.add_argument(
        "--calib-images",
        required=True,
        help="Directory of representative JPEG/PNG calibration images",
    )
    parser.add_argument("--imgsz", type=int, default=320)
    parser.add_argument(
        "--num-samples",
        type=int,
        default=200,
        help="Max number of calibration images to use",
    )
    parser.add_argument("--output", required=True, help="Output .tflite path")
    return parser.parse_args()


def letterbox(image, size: int):
    import cv2

    h, w = image.shape[:2]
    scale = min(size / w, size / h)
    resized = cv2.resize(image, (int(w * scale), int(h * scale)))
    canvas = np.full((size, size, 3), 114, dtype=np.uint8)
    pad_x = (size - resized.shape[1]) // 2
    pad_y = (size - resized.shape[0]) // 2
    canvas[pad_y: pad_y + resized.shape[0], pad_x: pad_x + resized.shape[1]] = resized
    return canvas


def main() -> None:
    args = parse_args()
    import cv2
    import tensorflow as tf

    image_dir = Path(args.calib_images)
    image_paths = sorted(
        [p for p in image_dir.glob("*") if p.suffix.lower() in (".jpg", ".jpeg", ".png")]
    )[: args.num_samples]
    if not image_paths:
        raise FileNotFoundError(f"No calibration images found in {image_dir}")

    def representative_dataset():
        for path in image_paths:
            image = cv2.imread(str(path))
            if image is None:
                continue
            image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
            image = letterbox(image, args.imgsz).astype(np.float32) / 255.0
            yield [np.expand_dims(image, axis=0)]

    converter = tf.lite.TFLiteConverter.from_saved_model(args.model) \
        if Path(args.model).is_dir() else None

    if converter is None:
        # Re-quantizing directly from an existing .tflite buffer is not
        # supported by TFLiteConverter; the typical path is to keep the
        # intermediate SavedModel from onnx2tf and quantize from there.
        raise SystemExit(
            "Pass the SavedModel directory produced by onnx2tf (not the "
            ".tflite file) as --model to re-quantize with a custom "
            "calibration set."
        )

    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_dataset
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.uint8
    converter.inference_output_type = tf.float32

    tflite_model = converter.convert()
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(tflite_model)
    print(f"INT8 model written to {output_path} ({len(tflite_model)} bytes)")


if __name__ == "__main__":
    main()
