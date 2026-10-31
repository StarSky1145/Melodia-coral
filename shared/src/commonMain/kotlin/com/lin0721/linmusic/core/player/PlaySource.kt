package com.lin0721.linmusic.core.player

import kotlinx.serialization.Serializable

// 当前队列的来源容器，供"正在播放"处显示名称并跳转；与仅用于显示的 playContext 相互独立
@Serializable
data class PlaySource(val kind: Kind, val id: Long, val name: String, val coverUrl: String = "") {

    enum class Kind { PLAYLIST, ALBUM }
}
