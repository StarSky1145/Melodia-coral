package com.lin0721.linmusic.feature.podcast.domain

// 订阅更新判定结果：有更新的电台，以及首次见到而需补写的基线
data class PodcastUpdateCheck(
    val updatedRadioIds: Set<Long>,
    val baseline: Map<Long, Long>
)

object PodcastSubscriptionUpdates {

    // 首次见到的电台以其最新一期为基线，不算更新；之后最新一期晚于已见时间才算
    fun check(radios: List<PodcastRadio>, seen: Map<Long, Long>): PodcastUpdateCheck {
        val updated = mutableSetOf<Long>()
        val baseline = mutableMapOf<Long, Long>()
        for (radio in radios) {
            val latest = radio.lastProgramCreateTimeMs
            if (latest <= 0) continue
            val seenAt = seen[radio.id]
            when {
                seenAt == null -> baseline[radio.id] = latest
                latest > seenAt -> updated += radio.id
            }
        }
        return PodcastUpdateCheck(updated, baseline)
    }
}
