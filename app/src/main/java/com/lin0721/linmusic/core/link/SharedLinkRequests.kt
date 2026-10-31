package com.lin0721.linmusic.core.link

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.share.NeteaseLink
import com.lin0721.linmusic.core.share.NeteaseLinkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "SharedLinkRequests"

// 从其他 App 分享/打开的网易云链接：MainActivity 收到后先挂起在这里，由界面层消费一次并跳转
class SharedLinkRequests {

    private val _pending = MutableStateFlow<NeteaseLink?>(null)
    val pending: StateFlow<NeteaseLink?> = _pending.asStateFlow()

    // 短链只需要读一次重定向地址，不跟随跳转，也不带任何登录 Cookie
    private val redirectClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    // 识别成功返回 true；文本里没有网易云链接返回 false，由调用方提示
    fun submit(text: String?): Boolean {
        val link = NeteaseLinkParser.parse(text) ?: return false
        _pending.value = link
        return true
    }

    fun consume(): NeteaseLink? = _pending.getAndUpdate { null }

    // 163cn.tv 短链换成真实地址再解析；解析不出来返回 null
    suspend fun resolve(link: NeteaseLink): NeteaseLink? {
        if (link !is NeteaseLink.ShortLink) return link
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(link.url).get().build()
                redirectClient.newCall(request).execute().use { response ->
                    NeteaseLinkParser.parse(response.header("Location"))?.takeIf { it !is NeteaseLink.ShortLink }
                }
            }.onFailure { AppLogger.w(TAG, "短链解析失败: ${link.url}", it) }.getOrNull()
        }
    }
}
