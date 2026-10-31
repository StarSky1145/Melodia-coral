package com.lin0721.linmusic.feature.library.data

import android.content.Context

// 沿用原 library_prefs 文件与键名，升级后置顶和排序不丢
class AndroidLibraryPreferences(context: Context) : LibraryPreferences {

    private val prefs = context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)

    override fun pinnedIds(): Set<String> = prefs.getStringSet(KEY_PINNED_IDS, emptySet()) ?: emptySet()

    override fun setPinnedIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_PINNED_IDS, ids).apply()
    }

    override fun customPlaylistOrder(): List<String> {
        val raw = prefs.getString(KEY_CUSTOM_ORDER, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(",")
    }

    override fun setCustomPlaylistOrder(order: List<String>) {
        prefs.edit().putString(KEY_CUSTOM_ORDER, order.joinToString(",")).apply()
    }

    override fun sortOrderName(): String? = prefs.getString(KEY_SORT_ORDER, null)

    override fun setSortOrderName(name: String) {
        prefs.edit().putString(KEY_SORT_ORDER, name).apply()
    }

    override fun isGridView(): Boolean = prefs.getBoolean(KEY_GRID_VIEW, false)

    override fun setGridView(isGrid: Boolean) {
        prefs.edit().putBoolean(KEY_GRID_VIEW, isGrid).apply()
    }

    private companion object {
        const val KEY_PINNED_IDS = "pinned_ids"
        const val KEY_CUSTOM_ORDER = "custom_playlist_order"
        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_GRID_VIEW = "is_grid_view"
    }
}
