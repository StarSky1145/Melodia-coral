package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

// 拖动排序状态：中线越过相邻行时交换并抵消位移，被拖行始终停在手指下；列表只能放可排序的行
@Stable
class DragReorderState internal constructor(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    fun start(key: Any) {
        draggingKey = key
        dragOffset = 0f
    }

    fun drag(deltaY: Float) {
        val key = draggingKey ?: return
        dragOffset += deltaY
        val visible = listState.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == key } ?: return
        val center = current.offset + dragOffset + current.size / 2f
        val target = visible.firstOrNull { item ->
            item.key != key && center >= item.offset && center <= item.offset + item.size
        } ?: return
        onMove(current.index, target.index)
        dragOffset -= (target.offset - current.offset)
    }

    fun end() {
        draggingKey = null
        dragOffset = 0f
    }
}

@Composable
fun rememberDragReorderState(listState: LazyListState, onMove: (from: Int, to: Int) -> Unit): DragReorderState {
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(listState) { DragReorderState(listState) { from, to -> currentOnMove(from, to) } }
}
