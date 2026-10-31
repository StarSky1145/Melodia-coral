package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "SessionMonitor"
private const val CONFIRM_DELAY_MS = 1_500L
private const val REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000
private const val SESSION_COOKIE = "MUSIC_U"

// 登录会话的守护：启动时校验并按间隔续期，运行中遇到 301 再核对一次，确认失效才清理本地登录态
class SessionMonitor(
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    // 常驻收集 301 事件；onExpired 在本地登录态清理之后回调
    suspend fun watch(onExpired: suspend () -> Unit): Unit = coroutineScope {
        launch { verifyAtStartup(onExpired) }
        AuthEvents.unauthorized.collect { handleUnauthorized(onExpired) }
    }

    suspend fun verifyAtStartup(onExpired: suspend () -> Unit) {
        if (!isLoggedInLocally()) return
        if (isSessionInvalid()) {
            expire(onExpired)
        } else {
            refreshIfDue()
        }
    }

    // 未登录时的 301 只是功能需要登录，不是过期
    suspend fun handleUnauthorized(onExpired: suspend () -> Unit) {
        if (!isLoggedInLocally()) return
        if (isSessionInvalid()) expire(onExpired)
    }

    private suspend fun isLoggedInLocally(): Boolean = userPreferences.userProfile.first() != null

    // 服务端明确答复未登录才算失效，并隔一小段时间复核一次防止偶发返回；网络错误等无法判断时按仍有效处理
    private suspend fun isSessionInvalid(): Boolean {
        if (!queryInvalid()) return false
        delay(CONFIRM_DELAY_MS)
        return queryInvalid()
    }

    private suspend fun queryInvalid(): Boolean = authRepository.getAccountInfo().first().fold(
        onSuccess = { it.profile == null },
        onFailure = { it is AppError.Unauthorized }
    )

    private suspend fun expire(onExpired: suspend () -> Unit) {
        AppLogger.w(TAG, "登录已失效，清理本地登录态")
        userPreferences.clearUserProfile()
        // 服务端会话已无效，这一步只为清空离线缓存，失败无碍
        runCatching { authRepository.logout().first() }
        onExpired()
    }

    private suspend fun refreshIfDue() {
        if (nowMs() - userPreferences.lastLoginRefreshAt.first() < REFRESH_INTERVAL_MS) return
        authRepository.refreshLogin().first()
            .onSuccess { headers ->
                val merged = mergeCookies(userPreferences.cookies.first(), headers)
                // 刷新响应异常时不覆盖现有会话
                if (merged.contains("$SESSION_COOKIE=")) {
                    userPreferences.saveCookies(merged)
                    userPreferences.saveLastLoginRefreshAt(nowMs())
                }
            }
            .onFailure { AppLogger.w(TAG, "登录续期失败", it) }
    }
}
