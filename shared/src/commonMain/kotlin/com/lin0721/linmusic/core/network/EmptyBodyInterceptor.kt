package com.lin0721.linmusic.core.network

import com.lin0721.linmusic.core.log.AppLogger
import okhttp3.Interceptor
import okhttp3.Response

private const val TAG = "EmptyBodyInterceptor"

// 只看响应开头：验证类响应的 code 一定是 JSON 第一个字段
private val VERIFICATION_REQUIRED_PREFIX = Regex("""^\s*\{\s*"code"\s*:\s*-462\b""")
private const val VERIFICATION_PREFIX_LENGTH = 40

// 拦截器：检测空响应体与网易云人机验证（-462）。
// 空响应会让 kotlinx.serialization 抛 EOF；-462 时 data 字段是对象而非业务结构，同样会被当成解析失败，
// 两种情况都在这里转成明确的异常，上层才能给出可操作的提示
class EmptyBodyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val bodyString = response.peekBody(Long.MAX_VALUE).string()
        if (bodyString.isBlank()) {
            AppLogger.w(TAG, "空响应体，疑似风控/未登录: ${request.url} code=${response.code}")
            throw ApiException("API body is empty, possibly auth failed")
        }
        if (VERIFICATION_REQUIRED_PREFIX.containsMatchIn(bodyString.take(VERIFICATION_PREFIX_LENGTH))) {
            AppLogger.w(TAG, "网易云要求安全验证(-462): ${request.url.encodedPath}")
            throw VerificationRequiredException()
        }
        
        return response
    }
}
