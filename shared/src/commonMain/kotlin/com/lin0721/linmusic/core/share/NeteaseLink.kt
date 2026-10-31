package com.lin0721.linmusic.core.share

// 从网易云分享链接 / 分享文案里识别出的目标
sealed interface NeteaseLink {
    data class Song(val id: Long) : NeteaseLink
    data class Playlist(val id: Long) : NeteaseLink
    data class Album(val id: Long) : NeteaseLink
    data class Artist(val id: Long) : NeteaseLink
    data class Radio(val id: Long) : NeteaseLink

    // 163cn.tv 短链，需要跟随一次重定向才能拿到真实地址
    data class ShortLink(val url: String) : NeteaseLink
}

// 识别网易云官方客户端"分享"出来的链接，支持纯链接与"分享xx的单曲《xx》: https://163cn.tv/xxx (来自@网易云音乐)"这类整段文案
object NeteaseLinkParser {

    private val URL_REGEX = Regex("""https?://[^\s"'<>()（）]+""", RegexOption.IGNORE_CASE)
    private val NETEASE_HOST = Regex("""^(?:[\w-]+\.)*music\.163\.com$""", RegexOption.IGNORE_CASE)
    private val SHORT_HOST = Regex("""^(?:www\.)?163cn\.tv$""", RegexOption.IGNORE_CASE)

    // 路径里的类型关键字 → 目标类型。顺序重要：djradio 要先于 dj 匹配
    private val TYPE_PATTERNS = listOf(
        "song" to { id: Long -> NeteaseLink.Song(id) },
        "playlist" to { id: Long -> NeteaseLink.Playlist(id) },
        "album" to { id: Long -> NeteaseLink.Album(id) },
        "artist" to { id: Long -> NeteaseLink.Artist(id) },
        "djradio" to { id: Long -> NeteaseLink.Radio(id) },
        "radio" to { id: Long -> NeteaseLink.Radio(id) },
    )

    fun parse(text: String?): NeteaseLink? {
        if (text.isNullOrBlank()) return null
        return URL_REGEX.findAll(text).firstNotNullOfOrNull { parseUrl(it.value) }
    }

    private fun parseUrl(rawUrl: String): NeteaseLink? {
        val url = rawUrl.trimEnd('.', ',', '，', '。', ')', '）')
        val afterScheme = url.substringAfter("://")
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        if (SHORT_HOST.matches(host)) return NeteaseLink.ShortLink(url)
        if (!NETEASE_HOST.matches(host)) return null

        // 网页版的真实路由在 # 之后（music.163.com/#/song?id=1），移动端在路径里（y.music.163.com/m/song?id=1）
        val route = afterScheme.substringAfter(host).replace("#", "")
        val path = route.substringBefore('?').trimEnd('/')
        val query = route.substringAfter('?', missingDelimiterValue = "")
        val lastSegment = path.substringAfterLast('/').lowercase()

        // 形如 /song/123 的路径式链接
        val pathId = lastSegment.toLongOrNull()
        val typeSegment = if (pathId != null) path.substringBeforeLast('/').substringAfterLast('/').lowercase() else lastSegment
        val id = pathId ?: queryValue(query, "id")?.toLongOrNull() ?: return null
        if (id <= 0) return null
        return TYPE_PATTERNS.firstOrNull { (type, _) -> typeSegment == type }?.second?.invoke(id)
    }

    private fun queryValue(query: String, key: String): String? =
        query.split('&').firstNotNullOfOrNull { pair ->
            val name = pair.substringBefore('=')
            if (name.equals(key, ignoreCase = true)) pair.substringAfter('=', "") else null
        }
}
