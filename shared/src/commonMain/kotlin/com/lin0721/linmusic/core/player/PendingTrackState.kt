package com.lin0721.linmusic.core.player

// 点击播放后先于播放器真实切歌显示目标曲目；真实曲目到达、失败或撤销时退场。只在主线程使用。
class PendingTrackState<T : Any>(private val idOf: (T) -> String) {

    var pending: T? = null
        private set

    // 进入待播状态前的播放意图，撤销时用来还原
    private var playWhenReadyBefore: Boolean? = null

    fun display(real: T?): T? = pending ?: real

    // 与真实曲目同一首时无需乐观显示，返回 false
    fun show(item: T, real: T?, playWhenReady: Boolean): Boolean {
        if (real != null && idOf(real) == idOf(item)) return false
        if (pending == null) playWhenReadyBefore = playWhenReady
        pending = item
        return true
    }

    // 真实曲目就是待播曲目、或真实曲目被清空时退场；其余真实切换不影响待播显示
    fun onRealTrack(real: T?) {
        val current = pending ?: return
        if (real == null || idOf(real) == idOf(current)) reset()
    }

    // 失败或撤销：清掉待播曲目，返回应还原的播放意图（原本没有待播时为 null）
    fun clear(): Boolean? {
        val restore = playWhenReadyBefore
        reset()
        return restore
    }

    private fun reset() {
        pending = null
        playWhenReadyBefore = null
    }
}
