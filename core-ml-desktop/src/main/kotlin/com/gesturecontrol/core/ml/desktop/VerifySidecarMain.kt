package com.gesturecontrol.core.ml.desktop

import java.io.File

/**
 * Manual verification harness for Stage 4 (project plan, §6b): starts the real sidecar against a
 * real webcam and logs every received frame to the console, so "real hand landmarks flow from a
 * physical desktop webcam into the JVM side" (the stage's own bar) can actually be seen, not just
 * assumed from the process not crashing. Run via `./gradlew :core-ml-desktop:run`.
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
    val client = HandTrackingSidecarClient(
        pythonExecutable = File(pythonPath),
        scriptPath = File(scriptPath),
        modelPath = File(modelPath),
        onResult = { landmarks, imageDimensions, timestampMs ->
            frameCount++
            if (landmarks != null) {
                handFrameCount++
                val wrist = landmarks.points.first()
                println(
                    "[$timestampMs ms] frame #$frameCount ($imageDimensions): " +
                        "hand=${landmarks.handedness} wrist=(${wrist.x}, ${wrist.y}, ${wrist.z})",
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
