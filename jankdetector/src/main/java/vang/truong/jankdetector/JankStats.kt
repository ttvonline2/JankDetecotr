package vang.truong.jankdetector

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Live frame statistics updated by [rememberJankDetector].
 */
@Stable
class JankStats {
    var fps by mutableIntStateOf(0)
    var jankCount by mutableIntStateOf(0)
    var droppedFrames by mutableIntStateOf(0)
    var lastFrameMs by mutableLongStateOf(0L)
    var isRunning by mutableStateOf(false)
}
