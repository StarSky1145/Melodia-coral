package com.lin0721.linmusic.core.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.VERIFICATION_REQUIRED_CODE
import com.lin0721.linmusic.core.network.isVerificationRequired
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "LoginViewModel"
private const val QR_LOGIN_URL_PREFIX = "https://music.163.com/login?codekey="
private const val POLL_INTERVAL_MS = 2500L
private const val QR_SIZE_PX = 480
private const val MAX_CONSECUTIVE_POLL_FAILURES = 5
// 验证码重发间隔，与网易云客户端一致
private const val SMS_RESEND_SECONDS = 60
private const val SMS_CODE_LENGTH = 4
// 中国大陆手机号：1 开头 11 位
private val MAINLAND_PHONE = Regex("^1\\d{10}$")
// 网易云要求人机验证时，验证码与扫码都会被拦，只能在网易云官方网页里完成验证后登录
private const val VERIFICATION_LOGIN_HINT = "网易云要求完成安全验证。请点「其他登录方式」改用网页登录，或过一段时间再试"
// 8821：需要行为验证码，与 -462 同属人机验证
private const val BEHAVIOR_CAPTCHA_CODE = 8821

sealed interface QrLoginState {
    data object Idle : QrLoginState
    data object Loading : QrLoginState
    // 二维码点阵，由界面层按平台转成图片
    data class WaitingScan(val qrMatrix: BitMatrix) : QrLoginState
    data class WaitingConfirm(val qrMatrix: BitMatrix) : QrLoginState
    data object Expired : QrLoginState
    data class Error(val message: String) : QrLoginState
}

// 短信验证码登录的界面状态
data class SmsLoginState(
    val sending: Boolean = false,
    // 大于 0 时为重发倒计时秒数
    val resendCountdown: Int = 0,
    // 当前这一轮验证码发往的手机号，用于提示
    val sentTo: String? = null,
    val loggingIn: Boolean = false,
    val error: String? = null,
)

// 登录弹窗内的二维码轮询状态机、短信验证码登录与 Cookie 提交校验，供 LoginBottomSheet 消费
class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _qrState = MutableStateFlow<QrLoginState>(QrLoginState.Idle)
    val qrState: StateFlow<QrLoginState> = _qrState.asStateFlow()

    private var pollJob: Job? = null

    private val _smsState = MutableStateFlow(SmsLoginState())
    val smsState: StateFlow<SmsLoginState> = _smsState.asStateFlow()

    private var countdownJob: Job? = null

    // 开始二维码登录：取 key → 本地生成二维码 → 轮询扫码状态
    fun startQrLogin(onLoginSuccess: (String) -> Unit) {
        stopQrPolling()
        _qrState.value = QrLoginState.Loading
        viewModelScope.launch {
            val key = authRepository.getQrKey().first().getOrNull()?.unikey
            if (key.isNullOrEmpty()) {
                _qrState.value = QrLoginState.Error("二维码生成失败，请重试")
                return@launch
            }
            val matrix = try {
                withContext(Dispatchers.Default) { generateQrMatrix(QR_LOGIN_URL_PREFIX + key) }
            } catch (e: Exception) {
                AppLogger.e(TAG, "二维码点阵生成失败", e)
                _qrState.value = QrLoginState.Error("二维码生成失败，请重试")
                return@launch
            }
            _qrState.value = QrLoginState.WaitingScan(matrix)
            pollQrStatus(key, matrix, onLoginSuccess)
        }
    }

    private fun pollQrStatus(key: String, matrix: BitMatrix, onLoginSuccess: (String) -> Unit) {
        pollJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (true) {
                delay(POLL_INTERVAL_MS)
                val result = authRepository.checkQrStatus(key).first()
                if (result.exceptionOrNull().isVerificationRequired()) {
                    _qrState.value = QrLoginState.Error(VERIFICATION_LOGIN_HINT)
                    return@launch
                }
                val response = result.getOrNull()
                if (response == null) {
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
                        _qrState.value = QrLoginState.Error("网络异常，请重试")
                        return@launch
                    }
                    continue
                }
                consecutiveFailures = 0
                when (response.code) {
                    800 -> {
                        _qrState.value = QrLoginState.Expired
                        return@launch
                    }
                    801 -> _qrState.value = QrLoginState.WaitingScan(matrix)
                    802 -> _qrState.value = QrLoginState.WaitingConfirm(matrix)
                    803 -> {
                        val cookies = response.cookies
                        if (cookies.isNullOrEmpty()) {
                            AppLogger.e(TAG, "二维码登录返回 803 但未解析到 Set-Cookie")
                            _qrState.value = QrLoginState.Error("登录成功但未获取到会话，请重试")
                        } else {
                            onLoginSuccess(cookies)
                        }
                        return@launch
                    }
                    VERIFICATION_REQUIRED_CODE, BEHAVIOR_CAPTCHA_CODE -> {
                        _qrState.value = QrLoginState.Error(VERIFICATION_LOGIN_HINT)
                        return@launch
                    }
                    else -> {
                        val errorMsg = response.message?.takeIf { it.isNotBlank() } ?: "扫码异常(${response.code})"
                        AppLogger.w(TAG, "二维码轮询异常响应: code=${response.code}, message=$errorMsg")
                        _qrState.value = QrLoginState.Error(errorMsg)
                        return@launch
                    }
                }
            }
        }
    }

    fun stopQrPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    // 弹窗关闭/切换登录方式时重置，避免下次打开残留上一次的二维码画面
    fun resetQrState() {
        stopQrPolling()
        _qrState.value = QrLoginState.Idle
    }

    // Cookie 登录：支持粘贴完整 Cookie 字符串，也支持只粘贴裸 MUSIC_U 值——
    // 浏览器里 MUSIC_U 是 HttpOnly，Cookie 编辑类工具通常只能单独复制到它的 Value，不含字段名和分号分隔的其他字段
    fun submitCookieLogin(rawCookie: String, onLoginSuccess: (String) -> Unit): Boolean {
        val trimmed = rawCookie.trim()
        val cookieString = when {
            trimmed.isEmpty() -> return false
            trimmed.contains("MUSIC_U=") -> trimmed
            !trimmed.contains("=") && !trimmed.contains(";") && !trimmed.contains(" ") -> "MUSIC_U=$trimmed"
            else -> return false
        }
        onLoginSuccess(cookieString)
        return true
    }

    // 发送登录验证码；倒计时未结束或正在发送时忽略重复点击。
    // webDriver 不为空时由隐藏的网页登录页真正发码（绕风控），否则退回 weapi 直连
    fun sendSmsCode(phone: String, countryCode: String = "86", webDriver: SmsWebDriver? = null) {
        val current = _smsState.value
        if (current.sending || current.resendCountdown > 0) return
        val normalized = normalizePhone(phone)
        if (!isValidPhone(normalized, countryCode)) {
            _smsState.update { it.copy(error = "请输入正确的手机号") }
            return
        }
        _smsState.update { it.copy(sending = true, error = null) }
        if (webDriver != null) {
            // 回调约定在主线程，直接改状态即可
            webDriver.sendCode(normalized) { result ->
                if (result.isSuccess) {
                    _smsState.update { it.copy(sending = false, sentTo = normalized) }
                    startCountdown()
                } else {
                    _smsState.update {
                        it.copy(sending = false, error = smsErrorText(result.exceptionOrNull(), "验证码发送失败，请稍后重试"))
                    }
                }
            }
            return
        }
        viewModelScope.launch {
            val result = authRepository.sendCaptcha(normalized, countryCode).first()
            if (result.isSuccess) {
                _smsState.update { it.copy(sending = false, sentTo = normalized) }
                startCountdown()
            } else {
                _smsState.update {
                    it.copy(sending = false, error = smsErrorText(result.exceptionOrNull(), "验证码发送失败，请稍后重试"))
                }
            }
        }
    }

    // 手机号 + 验证码登录，成功后把 Cookie 交给调用方落盘。
    // webDriver 不为空时由隐藏的网页登录页提交登录
    fun loginWithSms(
        phone: String,
        code: String,
        onLoginSuccess: (String) -> Unit,
        countryCode: String = "86",
        webDriver: SmsWebDriver? = null
    ) {
        if (_smsState.value.loggingIn) return
        val normalized = normalizePhone(phone)
        val trimmedCode = code.trim()
        val error = when {
            !isValidPhone(normalized, countryCode) -> "请输入正确的手机号"
            trimmedCode.length < SMS_CODE_LENGTH || trimmedCode.any { !it.isDigit() } -> "请输入短信里的验证码"
            else -> null
        }
        if (error != null) {
            _smsState.update { it.copy(error = error) }
            return
        }
        _smsState.update { it.copy(loggingIn = true, error = null) }
        if (webDriver != null) {
            webDriver.login(normalized, trimmedCode) { result ->
                val cookies = result.getOrNull()
                if (!cookies.isNullOrEmpty()) {
                    _smsState.update { it.copy(loggingIn = false) }
                    onLoginSuccess(cookies)
                } else {
                    _smsState.update {
                        it.copy(loggingIn = false, error = smsErrorText(result.exceptionOrNull(), "登录失败，请重试"))
                    }
                }
            }
            return
        }
        viewModelScope.launch {
            val result = authRepository.loginWithCaptcha(normalized, trimmedCode, countryCode).first()
            val cookies = result.getOrNull()
            if (cookies != null) {
                _smsState.update { it.copy(loggingIn = false) }
                onLoginSuccess(cookies)
            } else {
                _smsState.update {
                    it.copy(loggingIn = false, error = smsErrorText(result.exceptionOrNull(), "登录失败，请重试"))
                }
            }
        }
    }

    // 用户改动输入时清掉上一次的错误提示
    fun clearSmsError() {
        if (_smsState.value.error != null) _smsState.update { it.copy(error = null) }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (remaining in SMS_RESEND_SECONDS downTo 1) {
                _smsState.update { it.copy(resendCountdown = remaining) }
                delay(1_000)
            }
            _smsState.update { it.copy(resendCountdown = 0) }
        }
    }

    private fun normalizePhone(phone: String): String = phone.filter { it.isDigit() }

    // 大陆手机号严格校验；其他国家/地区只做长度兜底，由服务端判定
    private fun isValidPhone(phone: String, countryCode: String): Boolean =
        if (countryCode == "86") MAINLAND_PHONE.matches(phone) else phone.length in 5..15

    // 服务端文案优先；没有文案时按错误类型给出可操作的提示
    private fun smsErrorText(error: Throwable?, fallback: String): String = when (error) {
        is AppError.BizError -> when (error.code) {
            VERIFICATION_REQUIRED_CODE, BEHAVIOR_CAPTCHA_CODE -> VERIFICATION_LOGIN_HINT
            else -> error.rawMsg?.takeIf { it.isNotBlank() }
                ?: if (error.code == 503) "验证码发送过于频繁，请稍后再试" else "$fallback（${error.code}）"
        }
        AppError.NetworkError -> "网络连接失败，请检查网络后重试"
        AppError.RiskControl -> "网易云安全策略拦截了本次请求，请稍后重试或改用二维码登录"
        else -> fallback
    }

    private fun generateQrMatrix(content: String): BitMatrix =
        MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX)

    override fun onCleared() {
        stopQrPolling()
        countdownJob?.cancel()
        super.onCleared()
    }
}
