package com.lin0721.linmusic.core.network

import java.io.IOException

// 自定义 API 异常
class ApiException(message: String) : IOException(message)

// 网易云人机验证：响应 {"code":-462,"message":"请完成验证操作"}。需要用户自己完成验证或换种方式登录，App 不能代为处理
const val VERIFICATION_REQUIRED_CODE = -462

const val VERIFICATION_REQUIRED_MESSAGE = "网易云要求完成安全验证，请登录后再试，或在登录页改用网页登录"

class VerificationRequiredException : IOException(VERIFICATION_REQUIRED_MESSAGE)

fun Throwable?.isVerificationRequired(): Boolean =
    this is AppError.BizError && code == VERIFICATION_REQUIRED_CODE
