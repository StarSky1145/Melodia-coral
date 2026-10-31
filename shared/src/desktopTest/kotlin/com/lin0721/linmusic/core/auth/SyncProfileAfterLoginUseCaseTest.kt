package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.network.AppError
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SyncProfileAfterLoginUseCaseTest {

    private lateinit var prefs: UserPreferences
    private lateinit var dir: File
    private lateinit var repo: FakeAuthRepository

    @Before
    fun setUp() {
        val (preferences, directory) = tempUserPreferences()
        prefs = preferences
        dir = directory
        repo = FakeAuthRepository()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun validCookieSavesCookieAndProfile() = runTest {
        repo.accountResults = listOf(Result.success(loggedInAccount()))
        val profile = SyncProfileAfterLoginUseCase(prefs, repo)("MUSIC_U=ok")
        assertEquals("测试用户", profile?.nickname)
        assertEquals("MUSIC_U=ok", prefs.cookies.first())
        assertEquals(1L, prefs.userProfile.first()?.uid)
    }

    @Test
    fun anonymousAnswerClearsTheJustSavedCookie() = runTest {
        repo.accountResults = listOf(Result.success(anonymousAccount()))
        assertNull(SyncProfileAfterLoginUseCase(prefs, repo)("MUSIC_U=bad"))
        assertNull(prefs.cookies.first())
        assertNull(prefs.userProfile.first())
    }

    @Test
    fun networkFailureAlsoClearsCookie() = runTest {
        repo.accountResults = listOf(Result.failure(AppError.NetworkError))
        assertNull(SyncProfileAfterLoginUseCase(prefs, repo)("MUSIC_U=whatever"))
        assertNull(prefs.cookies.first())
    }
}
