package vang.truong.jankdetector

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A floating HUD overlay that shows live FPS, jank stats, and the list of components
 * that were recomposing during jank frames.
 *
 * ---
 * **Basic usage** — just wrap any screen:
 * ```kotlin
 * JankOverlay {
 *     YourScreen()
 * }
 * ```
 *
 * **Deep detection** — tag components you want to track inside the screen:
 * ```kotlin
 * JankOverlay {
 *     LazyColumn {
 *         items(list) { item ->
 *             // Option A: wrapper composable
 *             TrackJank("HeavyCard") {
 *                 HeavyCard(item)
 *             }
 *             // Option B: modifier
 *             HeavyCard(modifier = Modifier.trackJank("HeavyCard"))
 *         }
 *     }
 * }
 * ```
 *
 * The HUD will show which tagged components recomposed most often during jank frames.
 *
 * ---
 * **FPS color legend:**
 * - 🟢 Green  ≥ 55 fps — smooth
 * - 🟡 Yellow ≥ 40 fps — mild jank
 * - 🔴 Red    < 40 fps  — heavy jank
 */
@Composable
fun JankOverlay(content: @Composable () -> Unit) {
    val reporter = remember { JankReporter() }
    val stats = rememberJankDetector(reporter = reporter)

    // Provide reporter to all children so TrackJank / Modifier.trackJank can find it
    CompositionLocalProvider(LocalJankReporter provides reporter) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            HudPanel(stats = stats, reporter = reporter)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal HUD
// ─────────────────────────────────────────────────────────────────────────────

private enum class HudMode { COLLAPSED, STATS, FULL }

@Composable
private fun HudPanel(stats: JankStats, reporter: JankReporter) {
    var mode by remember { mutableStateOf(HudMode.STATS) }

    val fpsColor by animateColorAsState(
        targetValue = when {
            stats.fps >= 55 -> Color(0xFF00C853)
            stats.fps >= 40 -> Color(0xFFFFD600)
            else            -> Color(0xFFFF1744)
        },
        animationSpec = tween(300),
        label = "fps_color"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 56.dp, end = 8.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xE6000000))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                .clickable {
                    mode = when (mode) {
                        HudMode.COLLAPSED -> HudMode.STATS
                        HudMode.STATS     -> HudMode.FULL
                        HudMode.FULL      -> HudMode.COLLAPSED
                    }
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // FPS — always visible
            Text(
                text = "${stats.fps} FPS",
                color = fpsColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )

            if (mode == HudMode.STATS || mode == HudMode.FULL) {
                Spacer(modifier = Modifier.height(6.dp))
                StatRow("Jank frames",    stats.jankCount.toString(),    Color(0xFFFF6D00))
                Spacer(modifier = Modifier.height(2.dp))
                StatRow("Dropped frames", stats.droppedFrames.toString(), Color(0xFFFF1744))
                Spacer(modifier = Modifier.height(2.dp))
                StatRow("Last frame",     "${stats.lastFrameMs}ms",       Color(0xFFCCCCCC))
            }

            if (mode == HudMode.FULL) {
                Spacer(modifier = Modifier.height(8.dp))
                ComponentLog(reporter)
            }

            Spacer(modifier = Modifier.height(4.dp))
            val hint = when (mode) {
                HudMode.COLLAPSED -> "tap to expand"
                HudMode.STATS     -> "tap for components"
                HudMode.FULL      -> "tap to collapse"
            }
            Text(hint, color = Color(0x66FFFFFF), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ComponentLog(reporter: JankReporter) {
    val components = reporter.jankComponents

    HorizontalDivider(color = Color(0x33FFFFFF), thickness = 0.5.dp)
    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Jank culprits", color = Color(0x99FFFFFF), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        if (components.isNotEmpty()) {
            Text(
                text = "reset",
                color = Color(0xFF64B5F6),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { reporter.reset() }
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    if (components.isEmpty()) {
        Text(
            text = "No jank detected yet.\nAdd TrackJank{} around\nsuspected components.",
            color = Color(0x66FFFFFF),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 13.sp
        )
    } else {
        // Header row
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("component", color = Color(0x88FFFFFF), fontSize = 8.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text("hits", color = Color(0x88FFFFFF), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.width(6.dp))
            Text("last ms", color = Color(0x88FFFFFF), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.height(2.dp))

        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
            items(components) { info ->
                val intensity = (info.occurrences.coerceAtMost(20).toFloat() / 20f)
                val rowColor = lerp(Color(0xFFFFD600), Color(0xFFFF1744), intensity)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Severity bar
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(10.dp)
                            .background(rowColor, RoundedCornerShape(1.dp))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = info.name,
                        color = rowColor,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = info.occurrences.toString(),
                        color = rowColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${info.lastFrameMs}ms",
                        color = Color(0xAAFFFFFF),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xAAFFFFFF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.width(8.dp))
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

/** Linear interpolation between two Colors. */
private fun lerp(start: Color, stop: Color, fraction: Float): Color = Color(
    red   = start.red   + (stop.red   - start.red)   * fraction,
    green = start.green + (stop.green - start.green) * fraction,
    blue  = start.blue  + (stop.blue  - start.blue)  * fraction,
    alpha = 1f
)
