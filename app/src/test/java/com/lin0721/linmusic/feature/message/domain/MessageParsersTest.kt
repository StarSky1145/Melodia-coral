package com.lin0721.linmusic.feature.message.domain

import com.lin0721.linmusic.feature.message.data.MessageCommentDto
import com.lin0721.linmusic.feature.message.data.MessageNoticeDto
import com.lin0721.linmusic.feature.message.data.MessageUserDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageParsersTest {

    private fun notice(id: Long = 1, time: Long = 1000, body: String?) =
        MessageNoticeDto(id = id, time = time, notice = body)

    @Test
    fun `threadId解析单曲id，非单曲或格式异常返回null`() {
        assertEquals(12345L, parseSongIdFromThreadId("R_SO_4_12345"))
        assertNull(parseSongIdFromThreadId("R_PL_2_12345"))
        assertNull(parseSongIdFromThreadId("R_SO_4_"))
        assertNull(parseSongIdFromThreadId("R_SO_4_12x"))
        assertNull(parseSongIdFromThreadId(""))
    }

    @Test
    fun `评论类通知的发起人取外层user，评论内容与歌曲id来自我的评论`() {
        val item = parseNotice(
            notice(
                body = """
                {"type":6,"user":{"userId":11,"nickname":"甲","avatarUrl":"http://a/1.jpg"},
                 "comment":{"user":{"userId":22,"nickname":"我","avatarUrl":"http://a/2.jpg"},
                  "beRepliedUser":{"userId":33},"content":"我的评论","threadId":"R_SO_4_555","resourceType":4}}
                """.trimIndent()
            )
        )

        item as NoticeItem.CommentLike
        assertEquals(11L, item.user.uid)
        assertEquals("甲", item.user.nickname)
        assertEquals("我的评论", item.commentContent)
        assertEquals(555L, item.songId)
        assertEquals(1000L, item.time)
    }

    @Test
    fun `非单曲的评论通知不带歌曲id`() {
        val item = parseNotice(
            notice(
                body = """
                {"type":6,"user":{"userId":11,"nickname":"甲"},
                 "comment":{"content":"内容","threadId":"A_PL_9_1"}}
                """.trimIndent()
            )
        )

        item as NoticeItem.CommentLike
        assertEquals(11L, item.user.uid)
        assertNull(item.songId)
    }

    @Test
    fun `动态通知优先取msg，缺失时用歌曲名兜底，都没有则文案为空`() {
        val withMsg = parseNotice(
            notice(body = """{"type":1,"user":{"userId":1},"track":{"json":"{\"msg\":\"新歌来了\",\"song\":{\"name\":\"某歌\"}}"}}""")
        ) as NoticeItem.EventLike
        val withSong = parseNotice(
            notice(body = """{"type":1,"user":{"userId":1},"track":{"json":"{\"msg\":\"\",\"song\":{\"name\":\"某歌\"}}"}}""")
        ) as NoticeItem.EventLike
        val empty = parseNotice(
            notice(body = """{"type":1,"user":{"userId":1},"track":{"json":"not json"}}""")
        ) as NoticeItem.EventLike

        assertEquals("新歌来了", withMsg.eventText)
        assertEquals("分享单曲《某歌》", withSong.eventText)
        assertEquals("", empty.eventText)
    }

    @Test
    fun `动态文案开头的零宽字符与换行被清除，全是零宽字符时按无文案处理`() {
        val leading = parseNotice(
            notice(body = """{"type":1,"user":{"userId":1},"track":{"json":"{\"msg\":\"\\u200b\\n正文\\n\"}"}}""")
        ) as NoticeItem.EventLike
        val onlyZeroWidth = parseNotice(
            notice(body = """{"type":1,"user":{"userId":1},"track":{"json":"{\"msg\":\"\\u200b\\n\"}"}}""")
        ) as NoticeItem.EventLike

        assertEquals("正文", leading.eventText)
        assertEquals("", onlyZeroWidth.eventText)
    }

    @Test
    fun `歌单更新通知解析名称与曲目数`() {
        val item = parseNotice(
            notice(body = """{"type":2,"user":{"userId":7,"nickname":"丙"},"playlist":{"name":"我的歌单","trackCount":18}}""")
        ) as NoticeItem.PlaylistCollected

        assertEquals("我的歌单", item.playlistName)
        assertEquals(18, item.trackCount)
        assertEquals(7L, item.user.uid)
    }

    @Test
    fun `未知类型、非法JSON、空内容、缺少用户或缺少主体都降级为Unsupported`() {
        val bodies = listOf(
            """{"type":99,"user":{"userId":1}}""",
            "这不是JSON",
            "[1,2,3]",
            "",
            null,
            """{"type":6,"comment":{"content":"x"}}""",
            """{"type":6,"user":{"userId":1}}""",
            """{"type":1,"user":{"userId":1}}""",
            """{"type":2,"user":{"userId":1}}"""
        )

        bodies.forEachIndexed { index, body ->
            val item = parseNotice(notice(id = index.toLong(), body = body))
            assertTrue("第 $index 项应为 Unsupported: $item", item is NoticeItem.Unsupported)
            assertEquals(index.toLong(), item.id)
        }
    }

    @Test
    fun `评论条目从resourceJson取歌名与歌手`() {
        val item = parseCommentMessage(
            MessageCommentDto(
                commentId = 9,
                content = "回复",
                time = 500,
                user = MessageUserDto(userId = 5, nickname = "丁", avatarUrl = "http://a/5.jpg"),
                beRepliedContent = "我的评论",
                resourceJson = """{"id":1,"name":"某歌","creator":"某歌手"}"""
            )
        )

        assertEquals("某歌", item.resourceName)
        assertEquals("某歌手", item.resourceCreator)
        assertEquals("我的评论", item.repliedContent)
        assertEquals(5L, item.user.uid)
    }

    @Test
    fun `resourceJson损坏或为空、被回复内容为空白时对应字段为null`() {
        val broken = parseCommentMessage(MessageCommentDto(commentId = 1, resourceJson = "{oops", beRepliedContent = "  "))
        val absent = parseCommentMessage(MessageCommentDto(commentId = 2, resourceJson = null))

        assertNull(broken.resourceName)
        assertNull(broken.repliedContent)
        assertNull(absent.resourceName)
        assertNull(absent.resourceCreator)
    }

    @Test
    fun `at我条目对缺失字段宽松处理`() {
        val empty = parseForward(3, JsonObject(emptyMap()))
        assertEquals(3L, empty.key)
        assertNull(empty.user)
        assertEquals("", empty.content)
        assertEquals(0L, empty.time)

        val obj = Json.parseToJsonElement(
            """{"user":{"userId":8,"nickname":"戊"},"eventTime":777,"json":"{\"msg\":\"@你了\"}"}"""
        ) as JsonObject
        val parsed = parseForward(4, obj)
        assertEquals(8L, parsed.user?.uid)
        assertEquals("@你了", parsed.content)
        assertEquals(777L, parsed.time)
    }
}
