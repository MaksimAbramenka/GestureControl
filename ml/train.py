#!/usr/bin/env python3
"""Train the GestureControl hand-gesture classifier and export it to LiteRT (.tflite).

Reproduces the exact pipeline used to build the model bundled with the app: a small MLP
(63 -> 32 -> 16 -> 4, ReLU, softmax) trained on the normalized 63-float landmark feature
vector, exported with fp16-quantized weights. See ml/README.md for the full walkthrough.

Usage:
    python train.py [--data training_data.csv] [--output models/gesture_classifier_fp16.tflite]
"""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

# TensorFlow's own import is notoriously slow (loading its native libraries can take well over
# ten seconds) and happens silently before any of this script's own code runs -- print something
# immediately so the terminal doesn't look hung during that gap.
print("Starting up -- loading TensorFlow and friends (this can take a while the first time)...")

import numpy as np
import tensorflow as tf
from ai_edge_litert.interpreter import Interpreter
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.model_selection import train_test_split

print("Libraries loaded.")

# Must match domain/gesture/GestureClass.kt's enum declaration order exactly --
# GestureClassifierOutput.interpret() does GestureClass.entries[argmax], so an
# out-of-order mismatch here is a silent wrong-state bug, not a crash.
GESTURE_CLASSES = ["IDLE", "HOVER", "DRAW", "ERASE", "FLING"]


def load_dataset(csv_path: Path) -> tuple[np.ndarray, np.ndarray]:
    total_rows = max(sum(1 for _ in open(csv_path)) - 1, 0)  # minus the header row

    features: list[list[float]] = []
    labels: list[int] = []
    seen_rows: set[tuple[str, tuple[float, ...]]] = set()
    duplicate_count = 0
    with open(csv_path, newline="") as f:
        reader = csv.DictReader(f)
        feature_columns = [name for name in (reader.fieldnames or []) if name != "label"]
        for i, row in enumerate(reader, start=1):
            if row["label"] not in GESTURE_CLASSES:
                raise ValueError(f"Unknown label '{row['label']}' -- expected one of {GESTURE_CLASSES}")

            row_features = tuple(float(row[col]) for col in feature_columns)
            dedup_key = (row["label"], row_features)
            if dedup_key in seen_rows:
                duplicate_count += 1
            else:
                seen_rows.add(dedup_key)
                features.append(list(row_features))
                labels.append(GESTURE_CLASSES.index(row["label"]))

            if i % 500 == 0 or i == total_rows:
                percent = 100 * i / total_rows if total_rows else 100
                print(f"\r  Reading rows... {i}/{total_rows} ({percent:.0f}%)", end="", flush=True)
    print()
    if duplicate_count:
        print(f"  Dropped {duplicate_count} exact-duplicate rows ({len(features)} unique rows kept)")

    return np.array(features, dtype=np.float32), np.array(labels, dtype=np.int64)


def build_model(input_dim: int, num_classes: int) -> tf.keras.Model:
    model = tf.keras.Sequential(
        [
            tf.keras.layers.Input(shape=(input_dim,)),
            tf.keras.layers.Dense(32, activation="relu"),
            tf.keras.layers.Dense(16, activation="relu"),
            tf.keras.layers.Dense(num_classes, activation="softmax"),
        ]
    )
    model.compile(optimizer="adam", loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    return model


def sanity_check_tflite(tflite_model: bytes, x_val: np.ndarray, keras_predictions: np.ndarray) -> int:
    """Compares the exported model's predictions against the Keras model's on a sample of the
    validation set, before this gets anywhere near the Android app. Returns the mismatch count."""
    interpreter = Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    input_index = interpreter.get_input_details()[0]["index"]
    output_index = interpreter.get_output_details()[0]["index"]

    sample_count = min(50, len(x_val))
    mismatches = 0
    for i in range(sample_count):
        interpreter.set_tensor(input_index, x_val[i : i + 1])
        interpreter.invoke()
        tflite_prediction = int(np.argmax(interpreter.get_tensor(output_index)[0]))
        if tflite_prediction != int(keras_predictions[i]):
            mismatches += 1

    print(f"Sanity check: {sample_count - mismatches}/{sample_count} exported-model predictions match the Keras model")
    return mismatches


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=Path(__file__).parent / "training_data.csv")
    parser.add_argument(
        "--output", type=Path, default=Path(__file__).parent / "models" / "gesture_classifier_fp16.tflite"
    )
    parser.add_argument("--epochs", type=int, default=60)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--no-quantize", action="store_true", help="Export full float32 weights instead of fp16."
    )
    args = parser.parse_args()

    tf.random.set_seed(args.seed)
    np.random.seed(args.seed)

    print(f"Loading {args.data} ...")
    features, labels = load_dataset(args.data)
    print(f"  {len(features)} rows, {features.shape[1]} features, classes: {GESTURE_CLASSES}")
    for i, name in enumerate(GESTURE_CLASSES):
        print(f"    {name}: {int((labels == i).sum())}")

    x_train, x_val, y_train, y_val = train_test_split(
        features, labels, test_size=0.2, random_state=args.seed, stratify=labels
    )

    model = build_model(input_dim=features.shape[1], num_classes=len(GESTURE_CLASSES))
    model.summary()

    early_stop = tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=8, restore_best_weights=True)
    model.fit(
        x_train,
        y_train,
        validation_data=(x_val, y_val),
        epochs=args.epochs,
        callbacks=[early_stop],
        verbose=2,
    )

    val_predictions = np.argmax(model.predict(x_val, verbose=0), axis=1)
    print("\nValidation classification report:")
    print(classification_report(y_val, val_predictions, target_names=GESTURE_CLASSES))
    print("Confusion matrix (rows=true, cols=predicted):")
    print(confusion_matrix(y_val, val_predictions))

    print(f"\nConverting to LiteRT ({'float32' if args.no_quantize else 'fp16'}) ...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    if not args.no_quantize:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    tflite_model = converter.convert()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(tflite_model)
    print(f"Wrote {args.output} ({len(tflite_model)} bytes)")

    mismatches = sanity_check_tflite(tflite_model, x_val, val_predictions)
    if mismatches > 0:
        print(
            "WARNING: exported model disagrees with the trained model on some examples -- "
            "inspect before shipping.",
            file=sys.stderr,
        )


if __name__ == "__main__":
    main()
