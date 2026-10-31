package com.lin0721.linmusic.core.auth

/**
 * 手机号验证码登录的「网页驱动」抽象。
 *
 * 走纯 weapi 发码/登录经常被网易云风控直接拦掉，唯一稳定能过的是官方网页登录页
 * （页面自带一整套设备指纹与人机验证逻辑）。所以这里把「发码 / 登录」两个动作
 * 抽象成接口：Android 层用 WebView 打开网页登录页、把手机号和验证码填进去、
 * 点掉页面上的按钮，再从 Cookie 里捞 MUSIC_U；shared 层不依赖 WebView，只消费这个接口。
 *
 * 网易发码现在强制行为验证（接口直接返回 -462「请完成验证操作」），这一步只能由人完成，
 * 所以实现在需要时会把自己的窗口显示出来，让用户就地拖滑块，验证完再缩回去。
 *
 * 约定：实现方必须在主线程回调，且每次调用只回调一次（超时也要回调失败）。
 */
interface SmsWebDriver {

    /**
     * 让网页版登录页发出短信验证码。
     *
     * @param onProgress 过程提示，例如「请在上方完成滑块验证」，用于界面显示文案
     */
    fun sendCode(
        phone: String,
        onProgress: (String) -> Unit = {},
        onResult: (Result<Unit>) -> Unit
    )

    /** 让网页版登录页提交手机号 + 验证码，成功时回调 Cookie 字符串 */
    fun login(phone: String, code: String, onResult: (Result<String>) -> Unit)

    /** 释放 WebView，弹窗关闭时调用 */
    fun release()
}
