package vang.truong.jankdetector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Wraps [content] and reports every recomposition of it to the nearest [JankOverlay].
 *
 * Use this to identify which composables are recomposing during jank frames.
 * The [name] will appear in the JankOverlay HUD when jank is detected.
 *
 * Example:
 * ```
 * JankOverlay {
 *     LazyColumn {
 *         items(list) { item ->
 *             TrackJank("HeavyCard") {
 *                 HeavyCard(item)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param name    A label identifying this component in the jank report.
 * @param content The composable to track.
 */
@Composable
fun TrackJank(name: String, content: @Composable () -> Unit) {
    val reporter = LocalJankReporter.current
    // SideEffect runs after every successful recomposition of this scope
    SideEffect {
        reporter?.recordRecomposition(name)
    }
    content()
}

/**
 * Modifier version of [TrackJank]. Reports every recomposition of the composable
 * this modifier is attached to.
 *
 * Example:
 * ```
 * Box(modifier = Modifier.trackJank("HeavyBox")) { ... }
 * ```
 */
fun Modifier.trackJank(name: String): Modifier = composed {
    val reporter = LocalJankReporter.current
    SideEffect {
        reporter?.recordRecomposition(name)
    }
    Modifier
}
