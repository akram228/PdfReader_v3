package com.example.pdfreader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Fast page scrollbar. Drag the thumb (or tap the bar) to jump through a huge PDF.
 * Vertical along the right edge when pages scroll up/down; horizontal near the bottom for swipe pages.
 * It only takes touches inside its own thin bar, the rest of the screen still scrolls normally.
 */
@Composable
fun PageScrubber(
    count: Int,
    current: Int,
    vertical: Boolean,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (count < 2) return
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    var trackPx by remember { mutableIntStateOf(1) }
    val seekNow by rememberUpdatedState(onSeek)
    val countNow by rememberUpdatedState(count)
    val thumbLong = 44.dp
    val thumbLongPx = with(density) { thumbLong.toPx() }
    val usable = (trackPx - thumbLongPx).coerceAtLeast(1f)
    val usableNow by rememberUpdatedState(usable)

    val frac = (if (dragging) dragFrac else current.toFloat() / (count - 1)).coerceIn(0f, 1f)
    val off = (frac * usable).toInt()
    val shownPage = (frac * (count - 1)).toInt() + 1

    fun fromPos(pos: Float) {
        val f = ((pos - thumbLongPx / 2f) / usableNow).coerceIn(0f, 1f)
        dragFrac = f
        seekNow((f * (countNow - 1)).toInt())
    }

    Box(modifier.fillMaxSize()) {
        val strip = if (vertical) {
            Modifier
                .align(Alignment.CenterEnd)
                .width(36.dp)
                .fillMaxHeight()
                .padding(top = 64.dp, bottom = 56.dp)
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .height(36.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        }
        Box(
            strip
                .onSizeChanged { trackPx = if (vertical) it.height else it.width }
                .pointerInput(vertical) {
                    detectDragGestures(
                        onDragStart = { o -> dragging = true; fromPos(if (vertical) o.y else o.x) },
                        onDrag = { change, _ ->
                            change.consume()
                            fromPos(if (vertical) change.position.y else change.position.x)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    )
                }
                .pointerInput(vertical) {
                    detectTapGestures(onPress = { o -> fromPos(if (vertical) o.y else o.x) })
                }
        ) {
            // the bar
            Box(
                Modifier
                    .align(Alignment.Center)
                    .then(if (vertical) Modifier.width(4.dp).fillMaxHeight() else Modifier.height(4.dp).fillMaxWidth())
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Black.copy(alpha = 0.30f))
            )
            // the thumb
            Box(
                Modifier
                    .align(if (vertical) Alignment.TopCenter else Alignment.CenterStart)
                    .offset { if (vertical) IntOffset(0, off) else IntOffset(off, 0) }
                    .then(
                        if (vertical) Modifier.width(24.dp).height(thumbLong)
                        else Modifier.width(thumbLong).height(24.dp)
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = if (dragging) 0.95f else 0.75f))
            )
        }

        if (dragging) {
            val bubble = if (vertical) {
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, 64.dp.roundToPx() + off) }
                    .padding(end = 48.dp)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92.dp)
            }
            Text(
                "$shownPage / $count",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = bubble
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
