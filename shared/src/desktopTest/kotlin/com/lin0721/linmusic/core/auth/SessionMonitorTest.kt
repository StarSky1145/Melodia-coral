package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.network.AppError
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionMonitorTest {

    private lateinit var prefs: UserPreferences
    private lateinit var dir: File
    private lateinit var repo: FakeAuthRepository
    private var expiredCount = 0
    private var now = 1_000_000_000L

    @Before
    fun setUp() {
        val (preferences, directory) = tempUserPreferences()
        prefs = preferences
        dir = directory
        repo = FakeAuthRepository()
        expiredCount = 0
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun monitor() = SessionMonitor(prefs, repo) { now }

    private suspend fun login() {
        prefs.saveCookies("MUSIC_U=old; os=pc")
        prefs.saveUserProfile(UserProfile(uid = 1, nickname = "测试用户", avatarUrl = ""))
    }

    private val onExpired: suspend () -> Unit = { expiredCount++ }

    @Test
    fun unauthorizedWhileLoggedOutIsIgnored() = runTest {
        monitor().handleUnauthorized(onExpired)
        assertEquals(0, expiredCount)
        assertEquals(0, repo.accountCalls)
    }

    @Test
    fun serverSayingAnonymousExpiresSessionAfterConfirmation() = runTest {
        login()
        repo.accountResults = listOf(Result.success(anonymousAccount()))
        monitor().handleUnauthorized(onExpired)
        assertEquals(1, expiredCount)
        assertEquals(2, repo.accountCalls)
        assertEquals(1, repo.logoutCalls)
        assertNull(prefs.userProfile.first())
        assertNull(prefs.cookies.first())
    }

    @Test
    fun transientAnonymousAnswerDoesNotLogOut() = runTest {
        login()
        repo.accountResults = listOf(Result.success(anonymousAccount()), Result.success(loggedInAccount()))
        monitor().handleUnauthorized(onExpired)
        assertEquals(0, expiredCount)
        assertNotNull(prefs.userProfile.first())
    }

    @Test
    fun unauthorizedErrorExpiresSession() = runTest {
        login()
        repo.accountResults = listOf(Result.failure(AppError.Unauthorized))
        monitor().handleUnauthorized(onExpired)
        assertEquals(1, expiredCount)
    }

    @Test
    fun networkErrorKeepsSession() = runTest {
        login()
        repo.accountResults = listOf(Result.failure(AppError.NetworkError))
        monitor().handleUnauthorized(onExpired)
        assertEquals(0, expiredCount)
        assertEquals(1, repo.accountCalls)
        assertNotNull(prefs.cookies.first())
    }

    @Test
    fun startupRefreshMergesNewCookiesAndRecordsTime() = runTest {
        login()
        repo.accountResults = listOf(Result.success(loggedInAccount()))
        repo.refreshResult = Result.success(listOf("MUSIC_U=fresh; Max-Age=1296000; Path=/"))
        monitor().verifyAtStartup(onExpired)
        assertEquals("MUSIC_U=fresh; os=pc", prefs.cookies.first())
        assertEquals(now, prefs.lastLoginRefreshAt.first())
        assertEquals(0, expiredCount)
    }

    @Test
    fun recentRefreshIsSkipped() = runTest {
        login()
        prefs.saveLastLoginRefreshAt(now - 60_000)
        repo.accountResults = listOf(Result.success(loggedInAccount()))
        monitor().verifyAtStartup(onExpired)
        assertEquals(0, repo.refreshCalls)
    }

    @Test
    fun refreshFailureLeavesCookiesAlone() = runTest {
        login()
        repo.accountResults = listOf(Result.success(loggedInAccount()))
        repo.refreshResult = Result.failure(AppError.NetworkError)
        monitor().verifyAtStartup(onExpired)
        assertEquals("MUSIC_U=old; os=pc", prefs.cookies.first())
        assertEquals(0L, prefs.lastLoginRefreshAt.first())
    }

    @Test
    fun startupWithExpiredSessionClearsAndNotifies() = runTest {
        login()
        repo.accountResults = listOf(Result.success(anonymousAccount()))
        monitor().verifyAtStartup(onExpired)
        assertEquals(1, expiredCount)
        assertEquals(0, repo.refreshCalls)
    }

    @Test
    fun startupWhileLoggedOutDoesNothing() = runTest {
        monitor().verifyAtStartup(onExpired)
        assertEquals(0, repo.accountCalls)
    }
}
