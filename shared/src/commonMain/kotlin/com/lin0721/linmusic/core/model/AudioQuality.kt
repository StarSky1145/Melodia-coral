package com.lin0721.linmusic.core.model

// 音质代码转展示文案，被 settings/player 等多个域的音质相关 UI 复用。
fun getQualityDisplayName(quality: String): String {
    return when (quality) {
        "standard" -> "标准"
        "higher" -> "较高"
        "exhigh" -> "极高"
        "lossless" -> "无损 (FLAC)"
        "hires" -> "Hi-Res"
        "jyeffect" -> "高清环绕声"
        "sky" -> "沉浸环绕声"
        "jymaster" -> "超清母带"
        else -> quality
    }
}

// 音质代码转纯英文短标签，用于播放页等空间紧凑处；未知档位返回 null 以便隐藏
fun getQualityBadgeLabel(quality: String): String? {
    return when (quality) {
        "standard" -> "Standard"
        "higher" -> "Higher"
        "exhigh" -> "ExHigh"
        "lossless" -> "Lossless"
        "hires" -> "Hi-Res"
        "jyeffect" -> "Surround"
        "sky" -> "Immersive"
        "jymaster" -> "Master"
        else -> null
    }
}

// 可选下载音质档位，从低到高排列
val DOWNLOAD_QUALITY_LEVELS = listOf(
    "standard", "higher", "exhigh", "lossless", "hires", "jyeffect", "sky", "jymaster"
)

// 音质档位高低排序，未知档位返回 -1
fun qualityRank(level: String?): Int = level?.let(DOWNLOAD_QUALITY_LEVELS::indexOf) ?: -1

// 解码失败时的降档顺序：母带/环绕声先回到 Hi-Res，再逐级往下；已是最低档返回 null
fun lowerPlaybackLevel(level: String): String? = when (level) {
    "jymaster", "sky", "jyeffect" -> "hires"
    "hires" -> "lossless"
    "lossless" -> "exhigh"
    "exhigh" -> "higher"
    "higher" -> "standard"
    else -> null
}

// 用户偏好音质与上限取较低的一档；上限为空或未知档位时按偏好
fun cappedQuality(preferred: String, cap: String?): String {
    if (cap == null || qualityRank(cap) < 0) return preferred
    return if (qualityRank(preferred) > qualityRank(cap)) cap else preferred
}
