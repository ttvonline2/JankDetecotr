package vang.truong.jankdetector

import android.util.Log
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val JANK_THRESHOLD_MS = 17L

/**
 * Registers a [Choreographer.FrameCallback] that measures real frame timing.
 * Logs all frame events to ADB logcat under the tag **JankDetector**.
 *
 * @param reporter      Optional [JankReporter] for component-level deep detection.
 * @param onJankFrame   Optional callback fired on every jank frame with its duration in ms.
 */
@Composable
fun rememberJankDetector(
    reporter: JankReporter? = null,
    onJankFrame: ((frameDurationMs: Long) -> Unit)? = null
): JankStats {
    val stats = remember { JankStats() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val choreographer = Choreographer.getInstance()
        var lastFrameNanos = 0L
        var frameCount = 0
        var secondStartNanos = 0L
        var callbackRegistered = false

        Log.d(TAG, "[START] JankDetector attached.")

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!stats.isRunning) {
                    callbackRegistered = false
                    return
                }

                if (lastFrameNanos == 0L) {
                    // Baseline frame — skip measuring, just record start time
                    lastFrameNanos = frameTimeNanos
                    secondStartNanos = frameTimeNanos
                    choreographer.postFrameCallback(this)
                    return
                }

                val durationMs = (frameTimeNanos - lastFrameNanos) / 1_000_000L
                lastFrameNanos = frameTimeNanos
                frameCount++
                stats.lastFrameMs = durationMs

                if (durationMs > JANK_THRESHOLD_MS) {
                    stats.jankCount++
                    val dropped = (durationMs / 16L).toInt() - 1
                    if (dropped > 0) stats.droppedFrames += dropped

                    // Notify reporter first (it will log component-level details)
                    if (reporter != null) {
                        reporter.onJankFrame(durationMs)
                    } else {
                        // No reporter — log a plain jank line from the detector itself
                        val level = if (dropped >= 2) android.util.Log.ERROR else android.util.Log.WARN
                        Log.println(
                            level, TAG,
                            "[JANK] ${durationMs}ms frame (~$dropped dropped) " +
                                    "— add TrackJank{} inside JankOverlay to identify culprits"
                        )
                    }

                    onJankFrame?.invoke(durationMs)
                }

                // FPS update every ~1 second
                val elapsedMs = (frameTimeNanos - secondStartNanos) / 1_000_000L
                if (elapsedMs >= 1000L) {
                    stats.fps = frameCount
                    reporter?.logFpsSummary(frameCount, stats.jankCount, stats.droppedFrames)
                        ?: Log.i(TAG, "[FPS] ${frameCount} fps")
                    frameCount = 0
                    secondStartNanos = frameTimeNanos
                }

                choreographer.postFrameCallback(this)
            }
        }

        fun startChain() {
            lastFrameNanos = 0L
            frameCount = 0
            if (!callbackRegistered) {
                callbackRegistered = true
                choreographer.postFrameCallback(callback)
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "[RESUME] Restarting Choreographer chain.")
                    stats.isRunning = true
                    startChain()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "[PAUSE] Stopping Choreographer chain.")
                    stats.isRunning = false
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        stats.isRunning = true
        startChain()

        onDispose {
            Log.d(TAG, "[STOP] JankDetector detached.")
            stats.isRunning = false
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    return stats
}

