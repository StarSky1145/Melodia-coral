package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.api.Account
import com.lin0721.linmusic.core.api.AccountInfoResponse
import com.lin0721.linmusic.core.api.QrCheckResponse
import com.lin0721.linmusic.core.api.QrKeyResponse
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.preferences.PreferencesStores
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal fun loggedInAccount() = AccountInfoResponse(
    code = 200,
    account = Account(id = 1),
    profile = com.lin0721.linmusic.core.api.UserProfile(userId = 1, nickname = "测试用户")
)

internal fun anonymousAccount() = AccountInfoResponse(code = 200, account = null, profile = null)

internal fun tempUserPreferences(): Pair<UserPreferences, File> {
    val dir = Files.createTempDirectory("melodia-auth-test").toFile()
    return UserPreferences(PreferencesStores.get(File(dir, "user.preferences_pb"))) to dir
}

internal class FakeAuthRepository : AuthRepository {

    // 每次 getAccountInfo 依次取一个结果，取完后一直返回最后一个
    var accountResults: List<Result<AccountInfoResponse>> = listOf(Result.failure(AppError.NetworkError))
    var refreshResult: Result<List<String>> = Result.success(emptyList())
    var captchaResult: Result<Unit> = Result.success(Unit)
    var captchaLoginResult: Result<String> = Result.success("MUSIC_U=test; __csrf=abc")

    var accountCalls = 0
    var logoutCalls = 0
    var refreshCalls = 0
    val captchaRequests = mutableListOf<Pair<String, String>>()
    val captchaLogins = mutableListOf<Triple<String, String, String>>()

    override fun getAccountInfo(): Flow<Result<AccountInfoResponse>> = flow {
        val result = accountResults[minOf(accountCalls, accountResults.lastIndex)]
        accountCalls++
        emit(result)
    }

    override fun logout(): Flow<Result<Unit>> = flow {
        logoutCalls++
        emit(Result.success(Unit))
    }

    override fun getQrKey(): Flow<Result<QrKeyResponse>> = flow { emit(Result.failure(AppError.NetworkError)) }

    override fun checkQrStatus(key: String): Flow<Result<QrCheckResponse>> = flow { emit(Result.failure(AppError.NetworkError)) }

    override fun refreshLogin(): Flow<Result<List<String>>> = flow {
        refreshCalls++
        emit(refreshResult)
    }

    override fun sendCaptcha(phone: String, countryCode: String): Flow<Result<Unit>> = flow {
        captchaRequests += phone to countryCode
        emit(captchaResult)
    }

    override fun loginWithCaptcha(phone: String, captcha: String, countryCode: String): Flow<Result<String>> = flow {
        captchaLogins += Triple(phone, captcha, countryCode)
        emit(captchaLoginResult)
    }
}
