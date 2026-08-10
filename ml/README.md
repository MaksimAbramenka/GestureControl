# Training your own gesture classifier

The app's Data collection mode records labeled training rows straight from your own hand, and
`train.py` turns those into a `.tflite` model ready to drop back into the app. This is the exact
pipeline used to train the model the app ships with — same architecture, same feature vector,
same export settings.

## 1. Record your own data

1. Open the app, switch to **Data collection** mode.
2. Select a gesture class, hold the pose in front of the camera, and watch the per-hand counters
   climb. Do this for both hands, for all four classes (`IDLE`, `HOVER`, `DRAW`, `ERASE`).
3. Vary lighting, hand distance from the camera, and background across a couple of separate
   sittings rather than one continuous session — a classifier trained on one lighting setup in one
   room is a real risk for "works on the dev phone, falls apart elsewhere."
4. Once every (gesture, hand) combination hits its threshold, a **Share CSV** button unlocks. Use
   it to send `training_data.csv` to your computer (email, cloud drive, AirDrop, whatever's
   convenient).

## 2. Set up the training environment

Requires Python 3.10+.

```bash
cd ml
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

## 3. Train

```bash
python train.py --data /path/to/your/training_data.csv
```

This will:

- Load the CSV and print the class balance (watch for one class dominating — rebalance or record
  more of the underrepresented ones if so).
- Train a small MLP (`63 → Dense(32, ReLU) → Dense(16, ReLU) → Dense(4) → Softmax`) with an
  80/20 train/validation split and early stopping.
- Print a classification report and confusion matrix on the validation set — check this before
  trusting the model. A confused class pair here will show up as "one gesture keeps getting
  misread as another" on-device.
- Export to LiteRT with fp16-quantized weights, then sanity-check the exported model's predictions
  against the original Keras model on a sample of the validation set, catching any
  conversion-time regression before it reaches the app.
- Write the result to `models/gesture_classifier_fp16.tflite` (override with `--output`).

Useful flags:

```bash
python train.py --data training_data.csv --epochs 100      # train longer
python train.py --data training_data.csv --no-quantize      # export full float32 weights instead
```

## 4. Integrate the trained model

Copy the exported file into the app's bundled assets:

```bash
cp models/gesture_classifier_fp16.tflite \
   ../core-ml/gesture-classifier/src/main/assets/gesture_classifier.tflite
```

Rebuild and install (`./gradlew installDebug` from the project root), then verify on-device before
trusting it — the validation metrics above are a signal, not a guarantee.

## Notes

- The four gesture classes and their label strings must exactly match
  `domain/gesture/GestureClass.kt`'s enum declaration order (`IDLE`, `HOVER`, `DRAW`, `ERASE`).
  `train.py` hardcodes this order and will refuse to train on a CSV with an unrecognized label,
  but if you ever add/reorder gesture classes in the app, update `GESTURE_CLASSES` in `train.py`
  to match.
- If a gesture's *shape* changes (not just needing more data), discard that class's old rows
  rather than blending old and new shapes under one label — two different hand poses sharing a
  label will poison that class's decision boundary. This happened for real during development; see
  the project's `mediapipe-litert-pipeline` skill for the full story.
