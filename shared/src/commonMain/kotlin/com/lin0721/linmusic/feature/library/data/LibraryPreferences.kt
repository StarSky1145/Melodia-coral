package com.lin0721.linmusic.feature.library.data

// 音乐库的置顶、排序与视图偏好，同步读写
interface LibraryPreferences {
    fun pinnedIds(): Set<String>
    fun setPinnedIds(ids: Set<String>)
    fun customPlaylistOrder(): List<String>
    fun setCustomPlaylistOrder(order: List<String>)

    // 排序方式按枚举名存取，解析交给调用方
    fun sortOrderName(): String?
    fun setSortOrderName(name: String)
    fun isGridView(): Boolean
    fun setGridView(isGrid: Boolean)
}
