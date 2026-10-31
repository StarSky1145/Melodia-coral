package com.lin0721.linmusic.core.navigation

// 回退栈栈帧的保留策略：记录哪些栈帧仍在组合中、哪些仍在栈里，给出该释放的栈帧。
// 只在主线程使用。
class FrameRetention(private val maxRetained: Int) {

    init {
        require(maxRetained > 0) { "maxRetained 必须大于 0" }
    }

    // 按最近使用顺序排列，越靠后越新
    private val known = LinkedHashSet<Long>()
    private val composed = HashSet<Long>()
    // 根栈帧常驻，不占淘汰额度
    private val pinned = HashSet<Long>()

    fun enter(id: Long, evictable: Boolean) {
        touch(id)
        composed.add(id)
        if (!evictable) pinned.add(id)
    }

    fun isComposed(id: Long): Boolean = id in composed

    fun leave(id: Long) {
        composed.remove(id)
        touch(id)
    }

    // 返回需要释放的栈帧并同时登记为已释放：已出栈且不在组合中的，加上超出额度的最久未用栈帧
    fun release(liveIds: Set<Long>): List<Long> {
        val released = ArrayList<Long>()
        val iterator = known.iterator()
        while (iterator.hasNext()) {
            val id = iterator.next()
            if (id !in liveIds && id !in composed) {
                released.add(id)
                pinned.remove(id)
                iterator.remove()
            }
        }

        var retained = known.count { it !in pinned && it in liveIds }
        if (retained > maxRetained) {
            val iterator2 = known.iterator()
            while (iterator2.hasNext() && retained > maxRetained) {
                val id = iterator2.next()
                if (id in pinned || id in composed) continue
                released.add(id)
                iterator2.remove()
                retained--
            }
        }
        return released
    }

    private fun touch(id: Long) {
        known.remove(id)
        known.add(id)
    }
}
