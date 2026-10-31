package com.lin0721.linmusic.feature.localmusic.data

import com.lin0721.linmusic.feature.localmusic.data.db.LocalTrackEntity
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource

data class LocalLibrarySyncDiff(
    val upserts: List<LocalTrackEntity>,
    val deleteUris: Set<String>
)

// 只写入变化的条目；导入条目扫不到不代表已删，由 isImportedAlive 判断
fun computeLocalLibrarySyncDiff(
    existing: List<LocalTrackEntity>,
    scanned: List<LocalTrackEntity>,
    isImportedAlive: (LocalTrackEntity) -> Boolean
): LocalLibrarySyncDiff {
    val existingByUri = existing.associateBy { it.uri }
    val scannedUris = scanned.mapTo(HashSet()) { it.uri }

    val upserts = scanned.filter { existingByUri[it.uri] != it }
    val deleteUris = existing.asSequence()
        .filter { it.uri !in scannedUris }
        .filter { it.source != LocalTrackSource.IMPORTED.name || !isImportedAlive(it) }
        .mapTo(HashSet()) { it.uri }

    return LocalLibrarySyncDiff(upserts, deleteUris)
}
