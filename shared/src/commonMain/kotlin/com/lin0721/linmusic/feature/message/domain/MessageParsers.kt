package com.lin0721.linmusic.feature.message.domain

import com.lin0721.linmusic.feature.message.data.MessageCommentDto
import com.lin0721.linmusic.feature.message.data.MessageNoticeDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// 消息接口里大量字段是"字符串形式的 JSON"，这里统一二次解析；任何一步失败都降级，不向上抛异常

private const val NOTICE_TYPE_TRACK = 1
private const val NOTICE_TYPE_PLAYLIST = 2
private const val NOTICE_TYPE_COMMENT = 6

private val parserJson = Json { ignoreUnknownKeys = true; isLenient = true }

// 单曲评论的 threadId 形如 R_SO_4_<songId>
private val SONG_THREAD_REGEX = Regex("^R_SO_4_(\\d+)$")

fun parseSongIdFromThreadId(threadId: String): Long? =
    SONG_THREAD_REGEX.matchEntire(threadId)?.groupValues?.get(1)?.toLongOrNull()

fun parseCommentMessage(dto: MessageCommentDto): CommentMessage {
    val resource = parseObject(dto.resourceJson)
    return CommentMessage(
        commentId = dto.commentId,
        user = MessageUser(dto.user.userId, dto.user.nickname, dto.user.avatarUrl),
        content = dto.content,
        time = dto.time,
        repliedContent = dto.beRepliedContent?.takeIf { it.isNotBlank() },
        resourceName = resource?.string("name")?.takeIf { it.isNotBlank() },
        resourceCreator = resource?.string("creator")?.takeIf { it.isNotBlank() }
    )
}

fun parseNotice(dto: MessageNoticeDto): NoticeItem {
    val unsupported = NoticeItem.Unsupported(dto.id, dto.time)
    val root = parseObject(dto.notice) ?: return unsupported
    val rootUser = parseUser(root.obj("user")) ?: return unsupported
    return when (root.int("type")) {
        NOTICE_TYPE_COMMENT -> parseCommentLikeNotice(dto, root, rootUser) ?: unsupported
        NOTICE_TYPE_TRACK -> parseEventLikeNotice(dto, root, rootUser) ?: unsupported
        NOTICE_TYPE_PLAYLIST -> parsePlaylistCollectedNotice(dto, root, rootUser) ?: unsupported
        else -> unsupported
    }
}

// @我 元素结构暂无真实样本，字段名按动态结构推测，拿到样本后校正
fun parseForward(key: Long, obj: JsonObject): ForwardMessage {
    val innerJson = parseObject(obj.string("json"))
    return ForwardMessage(
        key = key,
        user = parseUser(obj.obj("user")),
        content = innerJson?.string("msg") ?: obj.string("msg") ?: obj.string("content") ?: "",
        time = obj.long("eventTime") ?: obj.long("time") ?: 0L
    )
}

// 样本核实：comment 是"我的评论"（comment.user 恒为我自己），外层 user 才是发起动作的人
private fun parseCommentLikeNotice(dto: MessageNoticeDto, root: JsonObject, rootUser: MessageUser): NoticeItem? {
    val comment = root.obj("comment") ?: return null
    return NoticeItem.CommentLike(
        id = dto.id,
        time = dto.time,
        user = rootUser,
        commentContent = comment.string("content").orEmpty(),
        songId = comment.string("threadId")?.let(::parseSongIdFromThreadId)
    )
}

private fun parseEventLikeNotice(dto: MessageNoticeDto, root: JsonObject, rootUser: MessageUser): NoticeItem? {
    val track = root.obj("track") ?: return null
    val inner = parseObject(track.string("json"))
    val songName = inner?.obj("song")?.string("name")?.takeIf { it.isNotBlank() }
    val text = inner?.string("msg")?.let(::cleanDisplayText)?.takeIf { it.isNotEmpty() }
        ?: songName?.let { "分享单曲《$it》" }
        .orEmpty()
    return NoticeItem.EventLike(dto.id, dto.time, rootUser, text)
}

private fun parsePlaylistCollectedNotice(dto: MessageNoticeDto, root: JsonObject, rootUser: MessageUser): NoticeItem? {
    val playlist = root.obj("playlist") ?: return null
    return NoticeItem.PlaylistCollected(
        id = dto.id,
        time = dto.time,
        user = rootUser,
        playlistName = playlist.string("name").orEmpty(),
        trackCount = playlist.int("trackCount") ?: 0
    )
}

// 动态文案常以零宽空格 + 换行开头，String.trim() 不处理零宽字符
private val ZERO_WIDTH_REGEX = Regex("[\u200B-\u200D\u2060\uFEFF]")

private fun cleanDisplayText(raw: String): String = raw.replace(ZERO_WIDTH_REGEX, "").trim()

private fun parseUser(obj: JsonObject?): MessageUser? {
    obj ?: return null
    val uid = obj.long("userId")?.takeIf { it > 0 } ?: return null
    return MessageUser(uid, obj.string("nickname").orEmpty(), obj.string("avatarUrl").orEmpty())
}

private fun parseObject(raw: String?): JsonObject? {
    if (raw.isNullOrBlank()) return null
    return runCatching { parserJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
