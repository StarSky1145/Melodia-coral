package com.lin0721.linmusic.core.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.NeteaseEndpoints
import org.json.JSONObject

private const val TAG = "SmsWebDriver"

// 登录页是纯前端 SPA（首屏 HTML 里没有任何 input），WebView 要下完几 MB 的 chunk 才能渲染出表单，
// 超时给太紧就会「进度条卡住然后失败」，给太松又会让人干等，所以这里留 25s，
// 真正兜底的是 LoginViewModel 里的看门狗
private const val PAGE_LOAD_TIMEOUT_MS = 25_000L
private const val SEND_TIMEOUT_MS = 25_000L
private const val LOGIN_TIMEOUT_MS = 30_000L
private const val COOKIE_POLL_MS = 800L
private const val HOST_URL = "https://${NeteaseEndpoints.WEB_HOST}"

// 与 WebViewLoginScreen 一致的净化 UA：页面本身会按 UA 走移动端分支
private const val WEB_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/112.0.0.0 Mobile Safari/537.36"

// 驱动网页登录页填手机号/验证码并点按钮。
// 页面是纯前端渲染的，DOM 结构和类名会随版本变，所以全部按「可见元素 + 文案/placeholder」模糊匹配，
// 每一步都带重试；任何一步没对上都会回报 fail，由 Kotlin 侧转成可读提示。
private const val DRIVER_JS = """
(function () {
  var STEP = "__STEP__";
  var PHONE = "__PHONE__";
  var CODE = "__CODE__";
  var bridge = window.MELODIA_BRIDGE;
  function report(type, message) {
    try { bridge && bridge.report(JSON.stringify({ type: type, message: message || "" })); } catch (e) {}
  }
  function visible(el) {
    if (!el) return false;
    var r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) return false;
    var st = getComputedStyle(el);
    return st.display !== "none" && st.visibility !== "hidden" && parseFloat(st.opacity || "1") > 0.1;
  }
  function textOf(el) { return ((el.innerText || el.textContent || "") + "").trim(); }
  function byText(re) {
    var list = document.querySelectorAll("a,button,div,span,p,i,label,em");
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      var t = textOf(el);
      if (t && t.length <= 20 && re.test(t) && visible(el)) return el;
    }
    return null;
  }
  function inputs() {
    var list = [];
    var all = document.querySelectorAll("input");
    for (var i = 0; i < all.length; i++) if (visible(all[i])) list.push(all[i]);
    return list;
  }
  function setValue(el, v) {
    try {
      var proto = el.tagName === "TEXTAREA" ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
      var desc = Object.getOwnPropertyDescriptor(proto, "value");
      if (desc && desc.set) desc.set.call(el, v); else el.value = v;
      el.dispatchEvent(new Event("input", { bubbles: true }));
      el.dispatchEvent(new Event("change", { bubbles: true }));
      el.blur();
      return true;
    } catch (e) { return false; }
  }
  function hasCaptcha() {
    var sels = ["iframe[src*='verify']", "iframe[src*='encrypt-pages']", "iframe[src*='captcha']",
                "[class*='yidun']", "#captcha", "[id*='captcha']", "#captcha-login-box"];
    for (var i = 0; i < sels.length; i++) {
      try {
        var el = document.querySelector(sels[i]);
        if (el && visible(el)) return true;
      } catch (e) {}
    }
    // 网易把风控验证做成浮层，文案固定为「请完成验证操作」
    return /请完成验证|安全验证|拖动滑块|按住滑块/.test(document.body ? document.body.innerText || "" : "");
  }
  function findPhone() {
    var list = inputs();
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      var hint = (el.placeholder || "") + (el.name || "") + (el.id || "") + (el.className || "");
      if (el.type === "tel" || /手机|电话|phone|tel|mobile/i.test(hint)) return el;
    }
    return list.length > 0 ? list[0] : null;
  }
  function findCode(phoneEl) {
    var list = inputs();
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      if (el === phoneEl) continue;
      var hint = (el.placeholder || "") + (el.name || "") + (el.id || "") + (el.className || "");
      if (/验证码|code|captcha|sms/i.test(hint)) return el;
    }
    return list.length > 1 ? list[1] : null;
  }
  var tries = 0;
  var resultChecks = 0;
  function retry() {
    tries++;
    if (tries > 40) { report("fail", "没能在网页登录页找到手机号输入框"); return; }
    setTimeout(walk, 500);
  }
  function waitSendResult() {
    resultChecks++;
    if (hasCaptcha()) { report("captcha", "需要人机验证"); return; }
    var doneBtn = byText(/(重新获取|重发|已发送|\d+\s*[sS]\s*后)/);
    if (doneBtn) { report("sent", textOf(doneBtn)); return; }
    var errBtn = byText(/(发送失败|操作频繁|请稍后|错误)/);
    if (errBtn) { report("fail", textOf(errBtn)); return; }
    if (resultChecks > 16) { report("unknown", "没等到页面反馈"); return; }
    setTimeout(waitSendResult, 500);
  }
  function waitLoginResult() {
    resultChecks++;
    if (hasCaptcha()) { report("captcha", "需要人机验证"); return; }
    var errBtn = byText(/(验证码错误|登录失败|密码错误|账号异常)/);
    if (errBtn) { report("fail", textOf(errBtn)); return; }
    if (resultChecks > 10) { report("submitted", ""); return; }
    setTimeout(waitLoginResult, 500);
  }
  function walk() {
    var phoneEl = findPhone();
    if (!phoneEl) {
      var method = byText(/(手机号登录|短信验证码登录|验证码登录|手机登录)/);
      if (method) { method.click(); report("progress", "打开手机号登录"); }
      retry();
      return;
    }
    if (phoneEl.value !== PHONE) setValue(phoneEl, PHONE);
    if (STEP === "send") {
      var sendBtn = byText(/(获取验证码|发送验证码|点击获取|重新获取)/);
      if (!sendBtn) { retry(); return; }
      sendBtn.click();
      report("progress", "点击获取验证码");
      resultChecks = 0;
      setTimeout(waitSendResult, 1200);
      return;
    }
    var codeEl = findCode(phoneEl);
    if (!codeEl) { retry(); return; }
    setValue(codeEl, CODE);
    var loginBtn = byText(/(立即登录|登录\/注册|登\s*录)/);
    if (!loginBtn) { retry(); return; }
    loginBtn.click();
    report("progress", "点击登录");
    resultChecks = 0;
    setTimeout(waitLoginResult, 1200);
  }
  setTimeout(walk, 300);
})();
"""

/**
 * 注意：当前未接入界面使用，保留实现备用。
 *
 * 2026-10 实测：`api/sms/captcha/sent` 对任何号码都直接返回 -462「请完成验证操作」并给出
 * 易盾行为验证页（verifyType 40），隐藏页面里盲点按钮无法完成这一步，所以发码改为在
 * 可见的官方网页登录页里由用户完成（见 WebViewLoginScreen / WebLoginPrefill）。
 *
 * 用隐藏的 WebView 驱动网易云网页登录页完成「发码 / 登录」，成功后从 Cookie 里取 MUSIC_U。
 *
 * 页面放在一个全透明、不可触摸、移出屏幕的 Dialog 里：页面能正常渲染和执行 JS，
 * 但用户看到的、点到的仍然是我们自己的手机号登录界面。
 */
class AndroidSmsWebDriver(private val context: Context) : SmsWebDriver {

    private val main = Handler(Looper.getMainLooper())
    private val activity: Activity? = context.findActivity()

    private var dialog: Dialog? = null
    private var webView: WebView? = null
    private var pageReady = false
    private var pageQueued: (() -> Unit)? = null

    private var sendCallback: ((Result<Unit>) -> Unit)? = null
    private var loginCallback: ((Result<String>) -> Unit)? = null
    private var loginPolling = false

    private val loginPoller = object : Runnable {
        override fun run() {
            if (!loginPolling) return
            val cookies = CookieManager.getInstance().getCookie(HOST_URL)
            if (cookies != null && cookies.contains("MUSIC_U=")) {
                finishLogin(Result.success(cookies))
                return
            }
            main.postDelayed(this, COOKIE_POLL_MS)
        }
    }

    /** 没有 Activity 就拿不到窗口 token，Dialog 建不起来，调用方需要退回纯接口登录 */
    val available: Boolean = activity != null

    override fun sendCode(phone: String, onResult: (Result<Unit>) -> Unit) {
        main.post {
            if (!ensurePage()) {
                onResult(Result.failure(fail("网页登录组件初始化失败")))
                return@post
            }
            settlePendingCallbacks()
            sendCallback = onResult
            main.postDelayed({
                failSend("网页登录页没有响应，请改用网页登录")
            }, SEND_TIMEOUT_MS)
            inject(DRIVER_JS.replace("__STEP__", "send").replace("__PHONE__", phone).replace("__CODE__", ""))
        }
    }

    override fun login(phone: String, code: String, onResult: (Result<String>) -> Unit) {
        main.post {
            if (!ensurePage()) {
                onResult(Result.failure(fail("网页登录组件初始化失败")))
                return@post
            }
            settlePendingCallbacks()
            loginCallback = onResult
            main.postDelayed({
                if (loginCallback != null) {
                    failLogin("网页登录超时，请检查网络后重试，或改用「其他登录方式 → 网页登录」")
                }
            }, LOGIN_TIMEOUT_MS)
            inject(DRIVER_JS.replace("__STEP__", "login").replace("__PHONE__", phone).replace("__CODE__", code))
        }
    }

    override fun release() {
        main.post {
            loginPolling = false
            main.removeCallbacks(loginPoller)
            pageQueued = null
            settlePendingCallbacks()
            runCatching { webView?.stopLoading() }
            runCatching { dialog?.dismiss() }
            runCatching { webView?.destroy() }
            dialog = null
            webView = null
            pageReady = false
        }
    }

    // ======================= 内部 =======================

    private fun ensurePage(): Boolean {
        val act = activity ?: return false
        if (webView != null) return true
        val wv = createWebView()
        if (wv == null) return false
        webView = wv
        val d = Dialog(act, android.R.style.Theme_Translucent_NoTitleBar)
        d.setContentView(wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        d.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            w.setDimAmount(0f)
            val metrics = context.resources.displayMetrics
            val lp = w.attributes
            lp.width = metrics.widthPixels
            lp.height = metrics.heightPixels
            lp.gravity = Gravity.TOP or Gravity.START
            // 双保险：窗口整块透明 + 移出屏幕，用户完全看不到，也不会抢触摸
            lp.x = metrics.widthPixels
            lp.y = metrics.heightPixels
            lp.alpha = 0f
            w.attributes = lp
        }
        runCatching { d.show() }.onFailure {
            AppLogger.e(TAG, "隐藏的网页登录窗口创建失败", it)
            webView = null
            return false
        }
        dialog = d
        wv.loadUrl(NeteaseEndpoints.LOGIN_URL)
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView? = try {
        WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = WEB_UA
            }
            CookieManager.getInstance().let { cm ->
                cm.setAcceptCookie(true)
                cm.setAcceptThirdPartyCookies(this, true)
            }
            addJavascriptInterface(Bridge(), "MELODIA_BRIDGE")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                    pageQueued?.let { queued ->
                        pageQueued = null
                        main.postDelayed(queued, 600)
                    }
                }
            }
        }
    } catch (e: Exception) {
        AppLogger.e(TAG, "WebView 创建失败", e)
        null
    }

    // 页面没加载完就先排队，避免把脚本注入到空白页
    private fun inject(script: String) {
        val action: () -> Unit = {
            runCatching { webView?.evaluateJavascript(script, null) }
            Unit
        }
        if (pageReady) {
            main.postDelayed(action, 400)
        } else {
            pageQueued = action
            main.postDelayed({
                if (!pageReady) {
                    pageQueued = null
                    failSend("网页登录页加载超时，请改用网页登录")
                    failLogin("网页登录页加载超时，请改用网页登录")
                }
            }, PAGE_LOAD_TIMEOUT_MS)
        }
    }

    private fun onReport(payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val type = json.optString("type")
        val message = json.optString("message")
        AppLogger.d(TAG, "网页登录回报: type=$type, message=$message")
        when (type) {
            "sent" -> if (sendCallback != null) finishSend(Result.success(Unit))
            "unknown" -> if (sendCallback != null) finishSend(Result.success(Unit))
            "captcha" -> {
                val text = "网易云要求人机验证，请在网页登录里完成（点下方「改用网页登录」）"
                failSend(text)
                failLogin(text)
            }
            "fail" -> {
                val text = message.ifBlank { "网页登录页操作失败，请改用「其他登录方式 → 网页登录」" }
                failSend(text)
                failLogin(text)
            }
            "submitted" -> startCookiePolling()
            else -> Unit
        }
    }

    private fun startCookiePolling() {
        if (loginCallback == null || loginPolling) return
        loginPolling = true
        main.post(loginPoller)
    }

    private fun finishSend(result: Result<Unit>) {
        val cb = sendCallback ?: return
        sendCallback = null
        cb(result)
    }

    private fun finishLogin(result: Result<String>) {
        val cb = loginCallback ?: return
        loginCallback = null
        loginPolling = false
        main.removeCallbacks(loginPoller)
        CookieManager.getInstance().flush()
        cb(result)
    }

    private fun failSend(text: String) = finishSend(Result.failure(fail(text)))

    private fun failLogin(text: String) = finishLogin(Result.failure(fail(text)))

    private fun fail(text: String) = AppError.BizError(-1, text)

    // 上一轮没回调就再来一次时，先把旧的失败掉，保证每次调用都只回调一次
    private fun settlePendingCallbacks() {
        sendCallback?.let { cb ->
            sendCallback = null
            cb(Result.failure(fail("上一次操作已被新的请求替换")))
        }
        loginCallback?.let { cb ->
            loginCallback = null
            loginPolling = false
            main.removeCallbacks(loginPoller)
            cb(Result.failure(fail("上一次操作已被新的请求替换")))
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun report(payload: String) {
            main.post { onReport(payload) }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
