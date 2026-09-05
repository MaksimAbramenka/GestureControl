"""Desktop hand-tracking sidecar (Phase 3b, Stage 4 -- see the project plan, section 6b).

Owns the webcam and MediaPipe's HandLandmarker directly, in a separate Python process, and emits
one JSON line per processed frame to stdout. Exists because MediaPipe's official Java Tasks Vision
artifact ships an Android-only native .so, and desktop C++ isn't an officially supported
HandLandmarker platform at all -- Python is (see the plan's Stage 0 spike, which verified this
exact approach: LIVE_STREAM mode, mediapipe==0.10.30 pinned around a real macOS Metal-calculator
crash in 1.0.x, against the same hand_landmarker.task file the Android/iOS apps already bundle).

Output contract, one line per processed frame:
    {"ts": <int ms>, "width": <int>, "height": <int>, "handedness": "Left"|"Right"|null,
     "landmarks": [[x, y, z], ...21 entries...] | null}
landmarks/handedness are null together when no hand is detected in that frame -- never partially
null, so the JVM side has exactly two cases to handle, not four.

Run standalone for manual verification:
    python hand_tracking_sidecar.py <path-to-hand_landmarker.task>
"""
import json
import sys
import time

import cv2
import mediapipe as mp
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision


def _emit(ts_ms: int, width: int, height: int, result: vision.HandLandmarkerResult) -> None:
    handedness = None
    landmarks = None
    if result.hand_landmarks:
        landmarks = [[lm.x, lm.y, lm.z] for lm in result.hand_landmarks[0]]
        if result.handedness and result.handedness[0]:
            handedness = result.handedness[0][0].category_name
    line = json.dumps({
        "ts": ts_ms,
        "width": width,
        "height": height,
        "handedness": handedness,
        "landmarks": landmarks,
    })
    sys.stdout.write(line + "\n")
    sys.stdout.flush()


def run(model_path: str, camera_index: int = 0) -> None:
    cap = cv2.VideoCapture(camera_index)
    if not cap.isOpened():
        sys.stderr.write(f"FATAL: could not open camera index {camera_index}\n")
        sys.exit(1)

    latest_frame_size = [0, 0]

    def on_result(result: vision.HandLandmarkerResult, output_image: mp.Image, timestamp_ms: int) -> None:
        _emit(timestamp_ms, latest_frame_size[0], latest_frame_size[1], result)

    base_options = mp_python.BaseOptions(model_asset_path=model_path, delegate=mp_python.BaseOptions.Delegate.CPU)
    options = vision.HandLandmarkerOptions(
        base_options=base_options,
        running_mode=vision.RunningMode.LIVE_STREAM,
        num_hands=1,
        result_callback=on_result,
    )
    landmarker = vision.HandLandmarker.create_from_options(options)

    start_time = time.time()
    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                sys.stderr.write("WARN: camera read failed, stopping\n")
                break

            latest_frame_size[0] = frame.shape[1]
            latest_frame_size[1] = frame.shape[0]
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            timestamp_ms = int((time.time() - start_time) * 1000)
            landmarker.detect_async(mp_image, timestamp_ms)
    except KeyboardInterrupt:
        pass
    finally:
        landmarker.close()
        cap.release()


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.stderr.write("usage: hand_tracking_sidecar.py <path-to-hand_landmarker.task> [camera_index]\n")
        sys.exit(2)
    camera_index = int(sys.argv[2]) if len(sys.argv) > 2 else 0
    run(sys.argv[1], camera_index)
