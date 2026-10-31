package com.lin0721.linmusic.core.userartist

import com.lin0721.linmusic.core.model.Artist
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// 关注歌手列表的网易云 Retrofit 接口定义。
interface UserArtistApi {

    // 获取已关注歌手 (需登录)
    @POST("/eapi/artist/sublist")
    suspend fun getArtistSublist(
        @Body body: ArtistSublistRequest = ArtistSublistRequest()
    ): ArtistSublistResponse
}

// 关注歌手每页条数。服务端默认仅返回 25 条，必须显式翻页才能取全
const val ARTIST_SUBLIST_PAGE_SIZE = 100

@Serializable
data class ArtistSublistRequest(
    val limit: Int = ARTIST_SUBLIST_PAGE_SIZE,
    val offset: Int = 0,
    val total: Boolean = true
)

@Serializable
data class ArtistSublistResponse(
    val code: Int = 0,
    // 实际返回结构：{"data":[...], "code":200}，data 字段直接就是歌手数组
    val data: List<Artist> = emptyList(),
    // 服务端是否还有下一页；老响应可能缺省该字段，此时按页大小推断
    val hasMore: Boolean? = null
)
