package com.gesturecontrol.core.ml.desktop

import com.gesturecontrol.domain.gesture.GestureSmoother
import java.io.File

/**
 * Manual verification harness for Stages 4-5 (project plan, §6b): starts the real sidecar against
 * a real webcam, classifies+smooths a gesture from every frame with a hand, and logs it to the
 * console -- "full gesture recognition verified against real hand poses on desktop" (Stage 5's own
 * bar), not just assumed from the classifier compiling. Run via `./gradlew :core-ml-desktop:run`.
 *
 * Camera access needs the same one-time OS permission grant the sidecar's own standalone run
 * already required -- this only works from a real interactive terminal for the same reason.
 */
fun main(args: Array<String>) {
    require(args.size == 3) { "usage: VerifySidecarMain <python> <script.py> <hand_landmarker.task>" }
    val (pythonPath, scriptPath, modelPath) = args

    println("Starting hand-tracking sidecar (Ctrl+C to stop)...")
    println("  python: $pythonPath")
    println("  script: $scriptPath")
    println("  model:  $modelPath")

    var frameCount = 0
    var handFrameCount = 0
    val smoother = GestureSmoother()
    val client = HandTrackingSidecarClient(
        pythonExecutable = File(pythonPath),
        scriptPath = File(scriptPath),
        modelPath = File(modelPath),
        onResult = { landmarks, imageDimensions, timestampMs ->
            frameCount++
            if (landmarks != null) {
                handFrameCount++
                val classified = DesktopGestureRecognizer.recognize(landmarks)
                val smoothed = smoother.smooth(classified.gestureClass)
                println(
                    "[$timestampMs ms] frame #$frameCount ($imageDimensions): hand=${landmarks.handedness} " +
                        "gesture=$smoothed (raw=${classified.gestureClass}, confidence=${classified.confidence})",
                )
            } else if (frameCount % 30 == 0) {
                println("[$timestampMs ms] frame #$frameCount ($imageDimensions): no hand")
            }
        },
        onUnexpectedExit = { exitCode ->
            println("Sidecar exited unexpectedly with code $exitCode after $frameCount frames -- see its stderr above.")
        },
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            println("Stopping sidecar -- saw $frameCount frames total, $handFrameCount with a hand.")
            client.stop()
        },
    )

    client.start()
    Thread.sleep(Long.MAX_VALUE)
}
