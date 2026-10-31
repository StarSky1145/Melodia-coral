package com.lin0721.linmusic.feature.podcast.domain

import com.lin0721.linmusic.feature.podcast.data.PodcastCategoryDto
import com.lin0721.linmusic.feature.podcast.data.PodcastCategoryGroupDto
import com.lin0721.linmusic.feature.podcast.data.PodcastProgramDto
import com.lin0721.linmusic.feature.podcast.data.PodcastProgramRankDto
import com.lin0721.linmusic.feature.podcast.data.PodcastRadioDetailDto
import com.lin0721.linmusic.feature.podcast.data.PodcastRadioDto

// 期号上限，超过即视为脏数据
private const val MAX_SERIAL_NUM = 100_000L

// 简介里的换行原样渲染会撑开卡片，压平交由 UI 控制行数
private fun String?.flattenLines(): String = orEmpty().lines().joinToString(" ") { it.trim() }.trim()

fun List<PodcastCategoryDto>.toPodcastCategories(): List<PodcastCategory> = mapNotNull { dto ->
    if (dto.id <= 0 || dto.name.isBlank()) return@mapNotNull null
    PodcastCategory(dto.id, dto.name)
}

// 没有可展示电台的分类整组丢弃
fun List<PodcastCategoryGroupDto>.toPodcastCategoryGroups(): List<PodcastCategoryGroup> = mapNotNull { dto ->
    if (dto.categoryId <= 0 || dto.categoryName.isBlank()) return@mapNotNull null
    val radios = dto.radios.toPodcastRadios()
    if (radios.isEmpty()) return@mapNotNull null
    PodcastCategoryGroup(dto.categoryId, dto.categoryName, radios)
}

// 榜单项的 program 缺失时跳过，名次由返回顺序体现
fun List<PodcastProgramRankDto>.toPodcastRankedPrograms(): List<PodcastProgram> =
    mapNotNull { it.program }.toPodcastPrograms()

// 缺封面的电台直接丢弃：货架卡片以封面为主体，占位图比少一张更难看
fun List<PodcastRadioDto>.toPodcastRadios(): List<PodcastRadio> = mapNotNull { dto ->
    if (dto.id <= 0 || dto.name.isBlank()) return@mapNotNull null
    val pic = dto.picUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    PodcastRadio(
        id = dto.id,
        name = dto.name,
        picUrl = pic,
        programCount = dto.programCount,
        subCount = dto.subCount,
        djName = dto.dj?.nickname.orEmpty(),
        recommendText = (dto.rcmdText?.takeIf { it.isNotBlank() } ?: dto.rcmdtext).flattenLines(),
        lastProgramName = dto.lastProgramName.orEmpty().trim(),
        lastProgramCreateTimeMs = dto.lastProgramCreateTime
    )
}

// 没有 mainSong 的节目点了播不出声，一律不渲染
fun List<PodcastProgramDto>.toPodcastPrograms(): List<PodcastProgram> = mapNotNull { it.toProgramOrNull() }

private fun PodcastProgramDto.toProgramOrNull(): PodcastProgram? {
    if (id <= 0 || name.isBlank()) return null
    val songId = mainSong?.id?.takeIf { it > 0 } ?: return null
    // 节目自身没封面时退回所属电台的封面，电台封面通常就是节目封面
    val cover = coverUrl?.takeIf { it.isNotBlank() }
        ?: radio?.picUrl?.takeIf { it.isNotBlank() }
        ?: return null

    return PodcastProgram(
        id = id,
        songId = songId,
        name = name,
        coverUrl = cover,
        durationMs = duration,
        createTimeMs = createTime,
        listenerCount = listenerCount,
        // 不是合理期号的脏值（如时间戳）按未知处理，界面不展示 0
        serialNum = serialNum.takeIf { it in 1..MAX_SERIAL_NUM }?.toInt() ?: 0,
        radioId = radio?.id ?: 0,
        radioName = radio?.name.orEmpty(),
        // 节目层的 dj 常缺省，退回电台层的主播
        djName = dj?.nickname?.takeIf { it.isNotBlank() }
            ?: radio?.dj?.nickname.orEmpty(),
        description = description.flattenLines()
    )
}

fun PodcastRadioDetailDto.toPodcastRadioDetail(): PodcastRadioDetail = PodcastRadioDetail(
    id = id,
    name = name,
    picUrl = picUrl.orEmpty(),
    desc = desc.flattenLines(),
    category = category.orEmpty(),
    programCount = programCount,
    subCount = subCount,
    djName = dj?.nickname.orEmpty(),
    djAvatarUrl = dj?.avatarUrl.orEmpty(),
    subscribed = subed
)
