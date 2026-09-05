package com.gesturecontrol.core.ml.desktop

import com.gesturecontrol.domain.hand.HandLandmarks
import com.gesturecontrol.domain.hand.ImageDimensions
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Owns the `hand_tracking_sidecar.py` subprocess (see that script's own doc comment, and the
 * project plan's §6b Stage 0/4) -- the desktop counterpart to `IosLiveHandLandmarker`/
 * `IosCameraCapture`, except here camera capture and hand tracking both happen inside the Python
 * process rather than as separate Kotlin components, since MediaPipe's official Java Tasks Vision
 * artifact is Android-only and Python is where a genuinely working desktop HandLandmarker path
 * actually exists. [onResult] fires once per line the sidecar emits, on the reader thread this
 * class owns -- not the JVM main/AWT-event thread -- mirroring how MediaPipe's own LIVE_STREAM
 * delegate callback on Android/iOS never runs on the UI thread either.
 */
class HandTrackingSidecarClient(
    private val pythonExecutable: File,
    private val scriptPath: File,
    private val modelPath: File,
    private val onResult: (HandLandmarks?, ImageDimensions, timestampMs: Long) -> Unit,
    // Fires if the sidecar's stdout closes on its own (crash, camera failure, ...) rather than
    // from a deliberate stop() -- without this, a dead sidecar looks identical from the caller's
    // side to one that's simply seeing no hand, which is exactly the silent-hang failure mode
    // this class originally shipped with (see Stage 4's own findings, project plan §6b).
    private val onUnexpectedExit: (exitCode: Int) -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true }

    private var process: Process? = null
    private var readerThread: Thread? = null

    @Volatile
    private var stopRequested = false

    /** Starts the sidecar and begins delivering [onResult] callbacks; a no-op if already
     * started. Camera access itself may prompt the OS's own one-time permission dialog the first
     * time this runs -- same as Android/iOS, not something this class can pre-empt or bypass.
     * Stderr is inherited (not merged into stdout, which is a pure JSON-lines stream) so a
     * camera/model failure on the Python side is visible in this process's own console/log rather
     * than silently swallowed. */
    fun start() {
        if (process != null) return
        stopRequested = false

        val started = ProcessBuilder(pythonExecutable.path, scriptPath.path, modelPath.path)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        process = started

        readerThread = Thread({ readLoop(started) }, "hand-tracking-sidecar-reader")
            .apply {
                isDaemon = true
                start()
            }
    }

    fun stop() {
        stopRequested = true
        readerThread?.interrupt()
        process?.destroy()
        process = null
        readerThread = null
    }

    private fun readLoop(process: Process) {
        process.inputStream.bufferedReader().use {
            var line = it.readLine()
            while (line != null && !Thread.currentThread().isInterrupted) {
                parseAndDeliver(line)
                line = it.readLine()
            }
        }
        if (!stopRequested) {
            onUnexpectedExit(process.waitFor())
        }
    }

    // A malformed or partial line (e.g. the process was killed mid-write) is dropped rather than
    // crashing the reader thread -- one bad frame from a long-running camera stream shouldn't take
    // the whole pipeline down, the same reasoning SidecarFrame.toDomain() already applies to a
    // structurally-wrong-but-parseable frame.
    private fun parseAndDeliver(line: String) {
        val frame = runCatching { json.decodeFromString<SidecarFrame>(line) }.getOrNull() ?: return
        onResult(frame.toDomain(), frame.imageDimensions(), frame.ts)
    }
}
