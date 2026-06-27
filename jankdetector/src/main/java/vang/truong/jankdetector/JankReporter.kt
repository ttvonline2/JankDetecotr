package vang.truong.jankdetector

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf

internal const val TAG = "JankDetector"

/**
 * Information about a component that was recomposing during jank frames.
 *
 * @param name         The tag passed to [TrackJank] or [Modifier.trackJank].
 * @param occurrences  How many jank frames this component was recomposing during.
 * @param lastFrameMs  Duration of the most recent jank frame where it was seen.
 * @param location     Source location captured when the component first recomposed.
 */
data class JankComponentInfo(
    val name: String,
    val occurrences: Int,
    val lastFrameMs: Long,
    val location: String = "unknown",
)

/**
 * Tracks which named components recompose and correlates recompositions with jank frames.
 * Logs all events to ADB logcat under the tag **JankDetector**.
 *
 * Log levels:
 * - `D` (Debug) — component first seen / registered
 * - `W` (Warn)  — jank frame detected with culprit list
 * - `E` (Error) — heavy jank (dropped ≥ 2 frames)
 * - `I` (Info)  — FPS summary every second
 */
@Stable
class JankReporter {

    // Recomposition counts in the current frame window
    private val windowCounts = mutableMapOf<String, Int>()

    // Source location captured on first recomposition per component name
    private val componentLocations = mutableMapOf<String, String>()

    /** Live list of components identified as jank contributors. Observable by Compose. */
    val jankComponents = mutableStateListOf<JankComponentInfo>()

    /**
     * Called by [TrackJank] / [Modifier.trackJank] on every recomposition.
     * The first time a component name is seen, captures its source location and logs it.
     */
    fun recordRecomposition(name: String) {
        windowCounts[name] = (windowCounts[name] ?: 0) + 1

        // First time this component is seen — capture caller and log
        if (!componentLocations.containsKey(name)) {
            val location = captureCallerLocation()
            componentLocations[name] = location
            Log.d(TAG, "[INIT] Tracking component: \"$name\"  ←  $location")
        }
    }

    /**
     * Called by [rememberJankDetector] whenever a jank frame is detected.
     * Logs detailed culprit information and updates [jankComponents].
     */
    fun onJankFrame(frameDurationMs: Long) {
        val droppedFrames = (frameDurationMs / 16L).toInt() - 1
        val culprits = windowCounts.filter { it.value > 0 }
            .entries.sortedByDescending { it.value }

        if (culprits.isEmpty()) {
            // Jank but no tracked component was recomposing — log as plain frame warning
            val level = if (droppedFrames >= 2) Log.ERROR else Log.WARN
            Log.println(
                level, TAG,
                "[JANK] ${frameDurationMs}ms frame (~$droppedFrames dropped) " +
                        "— no tracked components (add TrackJank{} to suspect components)"
            )
        } else {
            val sb = StringBuilder()
            sb.append("[JANK] ${frameDurationMs}ms frame (~$droppedFrames dropped frames)\n")
            sb.append("       Recomposing components during this frame:\n")
            culprits.forEach { (name, recomposeCount) ->
                val location = componentLocations[name] ?: "unknown location"
                sb.append("       ├─ \"$name\"  recomposed ${recomposeCount}x\n")
                sb.append("       │     $location\n")
            }
            sb.append("       └─ Total tracked: ${culprits.size} component(s)")

            val level = if (droppedFrames >= 2) Log.ERROR else Log.WARN
            Log.println(level, TAG, sb.toString())
        }

        // Update the in-app HUD list
        culprits.forEach { (name, _) ->
            val location = componentLocations[name] ?: "unknown"
            val existingIndex = jankComponents.indexOfFirst { it.name == name }
            if (existingIndex >= 0) {
                val existing = jankComponents[existingIndex]
                jankComponents[existingIndex] = existing.copy(
                    occurrences = existing.occurrences + 1,
                    lastFrameMs = frameDurationMs
                )
            } else {
                jankComponents.add(JankComponentInfo(name, 1, frameDurationMs, location))
            }
        }

        val sorted = jankComponents.sortedByDescending { it.occurrences }
        jankComponents.clear()
        jankComponents.addAll(sorted.take(15))

        windowCounts.clear()
    }

    /** Logs a one-line FPS summary. Called every second by [rememberJankDetector]. */
    internal fun logFpsSummary(fps: Int, totalJank: Int, totalDropped: Int) {
        val indicator = when {
            fps >= 55 -> "✅"
            fps >= 40 -> "⚠️"
            else      -> "🔴"
        }
        Log.i(TAG, "[FPS] $indicator ${fps} fps  |  jank frames: $totalJank  |  dropped: $totalDropped")
    }

    /** Clears all recorded data. */
    fun reset() {
        windowCounts.clear()
        componentLocations.clear()
        jankComponents.clear()
        Log.d(TAG, "[RESET] JankReporter data cleared.")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stack trace capture
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Walks the current thread's stack frames and returns the first frame that
     * belongs to user code (not JankDetector, not Compose internals, not stdlib).
     *
     * Result format:  `com.example.ui.HomeScreen.Content(HomeScreen.kt:42)`
     *
     * This runs only ONCE per unique component name, so the overhead is negligible.
     */
    private fun captureCallerLocation(): String {
        return try {
            Thread.currentThread().stackTrace
                .firstOrNull { frame ->
                    !isInternalFrame(frame.className)
                        && frame.fileName != null
                        && frame.lineNumber > 0
                }
                ?.let { frame ->
                    "${frame.className}.${frame.methodName}" +
                            "(${frame.fileName}:${frame.lineNumber})"
                }
                ?: "(location unavailable)"
        } catch (e: Exception) {
            "(location unavailable)"
        }
    }

    private fun isInternalFrame(className: String): Boolean {
        return className.startsWith("vang.truong.jankdetector") ||
               className.startsWith("java.lang.Thread") ||
               className.startsWith("androidx.compose") ||
               className.startsWith("kotlin.") ||
               className.startsWith("kotlinx.") ||
               className.startsWith("java.") ||
               className.startsWith("android.") ||
               className.startsWith("dalvik.") ||
               className.startsWith("com.android.")
    }
}

/**
 * Provides [JankReporter] down the composition tree.
 */
val LocalJankReporter = compositionLocalOf<JankReporter?> { null }
