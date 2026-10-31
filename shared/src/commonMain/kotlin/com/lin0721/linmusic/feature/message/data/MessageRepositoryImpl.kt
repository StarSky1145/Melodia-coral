package com.lin0721.linmusic.feature.message.data

import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.feature.message.domain.CommentMessage
import com.lin0721.linmusic.feature.message.domain.ForwardMessage
import com.lin0721.linmusic.feature.message.domain.MessagePage
import com.lin0721.linmusic.feature.message.domain.NoticeItem
import com.lin0721.linmusic.feature.message.domain.parseCommentMessage
import com.lin0721.linmusic.feature.message.domain.parseForward
import com.lin0721.linmusic.feature.message.domain.parseNotice
import kotlinx.coroutines.flow.Flow

class MessageRepositoryImpl(
    private val messageApi: MessageApi
) : MessageRepository {

    override fun getReceivedComments(
        uid: Long,
        cursor: Long,
        limit: Int
    ): Flow<Result<MessagePage<CommentMessage>>> = apiFlow(
        request = {
            messageApi.getReceivedComments(
                uid,
                MessageCommentsRequest(uid = uid, beforeTime = cursor.toString(), limit = limit)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            val items = response.comments.map(::parseCommentMessage)
            MessagePage(
                items = items,
                hasMore = response.more && items.isNotEmpty(),
                nextCursor = items.lastOrNull()?.time ?: cursor
            )
        }
    )

    override fun getForwards(cursor: Long, limit: Int): Flow<Result<MessagePage<ForwardMessage>>> = apiFlow(
        request = {
            messageApi.getForwards(MessageForwardsRequest(offset = cursor.toInt(), limit = limit))
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            val items = response.forwards.mapIndexed { index, obj -> parseForward(cursor + index, obj) }
            MessagePage(
                items = items,
                hasMore = response.more && items.isNotEmpty(),
                nextCursor = cursor + items.size
            )
        }
    )

    override fun getNotices(cursor: Long, limit: Int): Flow<Result<MessagePage<NoticeItem>>> = apiFlow(
        request = { messageApi.getNotices(MessageNoticesRequest(time = cursor, limit = limit)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            val items = response.notices.map(::parseNotice)
            MessagePage(
                items = items,
                hasMore = response.more && items.isNotEmpty() && response.lastTime > 0,
                nextCursor = response.lastTime
            )
        }
    )
}
