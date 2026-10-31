package com.lin0721.linmusic.feature.localmusic.domain

data class LocalScanFilter(
    val minDurationSec: Int,
    val excludedFolders: Set<String>
) {
    fun accepts(track: LocalTrack): Boolean = accepts(track.durationMs, track.folderPath)

    // 时长为 0 是元数据缺失，不当作短音频过滤；导入条目没有文件路径，不受目录排除影响
    fun accepts(durationMs: Long, folderPath: String?): Boolean {
        if (durationMs in 1 until minDurationSec * 1000L) return false
        return folderPath == null || folderPath !in excludedFolders
    }
}

// 扫描设置页展示的目录条目；trackCount 为该目录下全部曲目数（含被时长过滤掉的）
data class LocalFolder(
    val path: String,
    val trackCount: Int,
    val excluded: Boolean
) {
    val name: String get() = path.substringAfterLast('/').ifBlank { path }
}

data class AuthorizedFolder(
    val treeUri: String,
    val name: String,
    val importedCount: Int
)

val LocalTrack.folderPath: String?
    get() = folderPathOf(path)

fun folderPathOf(path: String?): String? = path?.substringBeforeLast('/', "")?.takeIf { it.isNotBlank() }
