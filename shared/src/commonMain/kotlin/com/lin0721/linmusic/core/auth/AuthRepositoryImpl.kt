package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.api.AccountInfoResponse
import com.lin0721.linmusic.core.api.CaptchaSentRequest
import com.lin0721.linmusic.core.api.CellphoneLoginRequest
import com.lin0721.linmusic.core.api.NeteaseApiService
import com.lin0721.linmusic.core.api.QrCheckRequest
import com.lin0721.linmusic.core.api.QrCheckResponse
import com.lin0721.linmusic.core.api.QrKeyResponse
import com.lin0721.linmusic.core.api.SmsLoginResponse
import com.lin0721.linmusic.core.cache.MetadataCache
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.network.mapToAppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.json.Json
import retrofit2.Response

private const val TAG = "AuthRepositoryImpl"

// 短信接口失败时常回非 2xx 状态码，错误文案在 errorBody 里，单独解析
private val errorBodyJson = Json { ignoreUnknownKeys = true }

class AuthRepositoryImpl(
    private val apiService: NeteaseApiService,
    private val metadataCache: MetadataCache
) : AuthRepository {

    override fun getAccountInfo(): Flow<Result<AccountInfoResponse>> = apiFlow(
        request = { apiService.getAccountInfo() },
        isSuccess = { it.code == 200 },
        code = { it.code },
        transform = { it }
    )

    // 调用方无论服务端是否登出成功都会清除本地登录态，离线缓存同步清空
    override fun logout(): Flow<Result<Unit>> = apiFlow(
        request = { apiService.logoutApi() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { Unit }
    ).onCompletion { metadataCache.clearAll() }

    override fun getQrKey(): Flow<Result<QrKeyResponse>> = apiFlow(
        request = { apiService.getQrKey() },
        isSuccess = { it.code == 200 },
        code = { it.code },
        transform = { it }
    )

    // 800/801/802/803 都是正常的轮询状态，不当业务错误处理；只有网络/解析异常才走 failure
    override fun refreshLogin(): Flow<Result<List<String>>> = flow {
        val response = apiService.refreshLogin()
        val body = response.body()
        when {
            body == null -> emit(Result.failure(AppError.NetworkError))
            body.code == 301 -> emit(Result.failure(AppError.Unauthorized))
            body.code != 200 -> emit(Result.failure(AppError.BizError(body.code, body.message)))
            else -> emit(Result.success(response.headers().values("Set-Cookie")))
        }
    }.catch { e ->
        AppLogger.e(TAG, "登录刷新异常: ${e::class.simpleName}", e)
        emit(Result.failure(mapToAppError(e)))
    }

    override fun sendCaptcha(phone: String, countryCode: String): Flow<Result<Unit>> = flow {
        val body = apiService.sendCaptcha(CaptchaSentRequest(cellphone = phone, ctcode = countryCode)).smsBody()
        when {
            body == null -> emit(Result.failure(AppError.NetworkError))
            body.code == 200 -> emit(Result.success(Unit))
            else -> {
                AppLogger.w(TAG, "验证码发送失败: code=${body.code}, message=${body.errorText}")
                emit(Result.failure(AppError.BizError(body.code, body.errorText)))
            }
        }
    }.catch { e ->
        AppLogger.e(TAG, "验证码发送异常: ${e::class.simpleName}", e)
        emit(Result.failure(mapToAppError(e)))
    }

    override fun loginWithCaptcha(phone: String, captcha: String, countryCode: String): Flow<Result<String>> = flow {
        val response = apiService.loginCellphone(
            CellphoneLoginRequest(phone = phone, captcha = captcha, countrycode = countryCode)
        )
        val body = response.smsBody()
        val cookies = response.headers().values("Set-Cookie").toCookieString()
        when {
            body == null -> emit(Result.failure(AppError.NetworkError))
            body.code != 200 -> {
                AppLogger.w(TAG, "验证码登录失败: code=${body.code}, message=${body.errorText}")
                emit(Result.failure(AppError.BizError(body.code, body.errorText)))
            }
            cookies == null -> {
                AppLogger.e(TAG, "验证码登录返回 200 但未解析到 Set-Cookie")
                emit(Result.failure(AppError.BizError(body.code, "登录成功但未获取到会话，请重试")))
            }
            else -> emit(Result.success(cookies))
        }
    }.catch { e ->
        AppLogger.e(TAG, "验证码登录异常: ${e::class.simpleName}", e)
        emit(Result.failure(mapToAppError(e)))
    }

    override fun checkQrStatus(key: String): Flow<Result<QrCheckResponse>> = flow {
        val response = apiService.checkQrStatus(QrCheckRequest(key = key))
        val body = response.body()
        if (body == null) {
            AppLogger.e(TAG, "二维码状态查询响应体为空，httpCode=${response.code()}")
            emit(Result.failure(AppError.NetworkError))
        } else {
            val cookies = response.headers().values("Set-Cookie").toCookieString()
            emit(Result.success(body.copy(cookies = cookies)))
        }
    }.catch { e ->
        AppLogger.e(TAG, "二维码状态查询异常: ${e::class.simpleName}", e)
        emit(Result.failure(mapToAppError(e)))
    }
}

// Set-Cookie 头只保留 name=value 部分，拼成请求用的 Cookie 字符串
private fun List<String>.toCookieString(): String? =
    map { it.substringBefore(";").trim() }
        .filter { it.contains("=") }
        .joinToString("; ")
        .takeIf { it.isNotEmpty() }

// 成功时取 body，非 2xx 时从 errorBody 解析出错误码与文案
private fun Response<SmsLoginResponse>.smsBody(): SmsLoginResponse? =
    body() ?: errorBody()?.string()?.let { raw ->
        runCatching { errorBodyJson.decodeFromString(SmsLoginResponse.serializer(), raw) }.getOrNull()
            ?: SmsLoginResponse(code = code())
    }
