package com.lin0721.linmusic.core.ui.components

/**
 * 手机号登录 → 网页登录 的一次性交接：用户在手机号框里输入的号码，
 * 打开官方网页登录页时预填进去，省得在网页里再输一遍。
 */
object WebLoginPrefill {
    @Volatile
    var phone: String? = null

    fun take(): String? {
        val value = phone
        phone = null
        return value
    }
}
