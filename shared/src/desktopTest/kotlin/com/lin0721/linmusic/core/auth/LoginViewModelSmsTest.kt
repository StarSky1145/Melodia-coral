package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.VerificationRequiredException
import com.lin0721.linmusic.core.network.mapToAppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val WEB_LOGIN_HINT = "网易云要求完成安全验证。请点「其他登录方式」改用网页登录，或过一段时间再试"

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelSmsTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeAuthRepository
    private lateinit var vm: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeAuthRepository()
        vm = LoginViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    @Test
    fun invalidPhoneIsRejectedWithoutRequest() = runVmTest {
        vm.sendSmsCode("12345")
        advanceUntilIdle()
        assertEquals("请输入正确的手机号", vm.smsState.value.error)
        assertTrue(repo.captchaRequests.isEmpty())
    }

    @Test
    fun sendingCodeStartsCountdownAndBlocksResend() = runVmTest {
        vm.sendSmsCode("138 0013 8000")
        runCurrent()
        assertEquals(listOf("13800138000" to "86"), repo.captchaRequests)
        assertEquals(60, vm.smsState.value.resendCountdown)
        assertEquals("13800138000", vm.smsState.value.sentTo)

        // 倒计时内重复点击不会再发请求
        vm.sendSmsCode("13800138000")
        runCurrent()
        assertEquals(1, repo.captchaRequests.size)

        advanceTimeBy(30_500)
        assertEquals(30, vm.smsState.value.resendCountdown)
        advanceUntilIdle()
        assertEquals(0, vm.smsState.value.resendCountdown)

        vm.sendSmsCode("13800138000")
        runCurrent()
        assertEquals(2, repo.captchaRequests.size)
        advanceUntilIdle()
    }

    @Test
    fun serverMessageIsShownWhenSendingFails() = runVmTest {
        repo.captchaResult = Result.failure(AppError.BizError(400, "发送验证码间隔过短"))
        vm.sendSmsCode("13800138000")
        advanceUntilIdle()
        assertEquals("发送验证码间隔过短", vm.smsState.value.error)
        assertEquals(0, vm.smsState.value.resendCountdown)
    }

    // 网络层把 -462 统一转成带服务端原文的 BizError，界面要给出可操作的引导而不是原文
    @Test
    fun verificationRequiredWhenSendingSuggestsWebLogin() = runVmTest {
        repo.captchaResult = Result.failure(mapToAppError(VerificationRequiredException()))
        vm.sendSmsCode("13800138000")
        advanceUntilIdle()
        assertEquals(WEB_LOGIN_HINT, vm.smsState.value.error)
        assertEquals(0, vm.smsState.value.resendCountdown)
    }

    @Test
    fun successfulLoginHandsCookiesToCaller() = runVmTest {
        var received: String? = null
        vm.loginWithSms("13800138000", " 1234 ", onLoginSuccess = { received = it })
        advanceUntilIdle()
        assertEquals("MUSIC_U=test; __csrf=abc", received)
        assertEquals(Triple("13800138000", "1234", "86"), repo.captchaLogins.single())
        assertNull(vm.smsState.value.error)
    }

    @Test
    fun shortCodeIsRejectedLocally() = runVmTest {
        vm.loginWithSms("13800138000", "12", onLoginSuccess = {})
        advanceUntilIdle()
        assertEquals("请输入短信里的验证码", vm.smsState.value.error)
        assertTrue(repo.captchaLogins.isEmpty())
    }

    @Test
    fun behaviorCaptchaSuggestsWebLogin() = runVmTest {
        repo.captchaLoginResult = Result.failure(AppError.BizError(8821, null))
        vm.loginWithSms("13800138000", "1234", onLoginSuccess = {})
        advanceUntilIdle()
        assertEquals(WEB_LOGIN_HINT, vm.smsState.value.error)

        vm.clearSmsError()
        assertNull(vm.smsState.value.error)
    }
}
