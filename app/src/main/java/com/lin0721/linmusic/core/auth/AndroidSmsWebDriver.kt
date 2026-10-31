package com.lin0721.linmusic.core.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
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

// 登录页是纯前端 SPA（首屏 HTML 里一个 input 都没有），WebView 下完几 MB 的 chunk 才渲染出表单
private const val PAGE_LOAD_TIMEOUT_MS = 25_000L
private const val SEND_TIMEOUT_MS = 25_000L
// 需要用户拖滑块时给足时间，别在验证过程中判超时
private const val CAPTCHA_TIMEOUT_MS = 120_000L
private const val LOGIN_TIMEOUT_MS = 30_000L
private const val COOKIE_POLL_MS = 800L
private const val HOST_URL = "https://${NeteaseEndpoints.WEB_HOST}"

// 与 WebViewLoginScreen 一致的净化 UA：页面本身会按 UA 走移动端分支
private const val WEB_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/112.0.0.0 Mobile Safari/537.36"

// 页面里复用的小工具：可见性判断、按文案找按钮、检测行为验证浮层
private const val PRELUDE_JS = """
(function () {
  window.__melodiaReport = function (type, message) {
    try {
      window.MELODIA_BRIDGE.report(JSON.stringify({ type: type, message: message || "" }));
    } catch (e) {}
  };
  window.__melodiaVisible = function (el) {
    if (!el) return false;
    var r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) return false;
    var st = getComputedStyle(el);
    return st.display !== "none" && st.visibility !== "hidden" && parseFloat(st.opacity || "1") > 0.1;
  };
  window.__melodiaByText = function (re) {
    var list = document.querySelectorAll("a,button,div,span,p,i,label,em");
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      var t = ((el.innerText || el.textContent || "") + "").trim();
      if (t && t.length <= 20 && re.test(t) && window.__melodiaVisible(el)) return el;
    }
    return null;
  };
  window.__melodiaHasVerify = function () {
    var sels = ["iframe[src*='verify']", "iframe[src*='encrypt-pages']", "iframe[src*='captcha']",
                "[class*='yidun']", "#captcha", "[id*='captcha']", "#captcha-login-box"];
    for (var i = 0; i < sels.length; i++) {
      try {
        var el = document.querySelector(sels[i]);
        if (el && window.__melodiaVisible(el)) return el;
      } catch (e) {}
    }
    var body = document.body ? (document.body.innerText || "") : "";
    if (/请完成验证|安全验证|拖动滑块|按住滑块/.test(body)) {
      return document.querySelector("#captcha-login-box") || document.body;
    }
    return null;
  };
})();
"""

// 驱动网页登录页填手机号再点「获取验证码」。页面纯前端渲染、类名会变，
// 所以全部按「可见元素 + 文案」模糊匹配并重试；对不上就回报 fail。
private const val DRIVER_JS = """
(function () {
  var PHONE = "__PHONE__";
  var report = window.__melodiaReport;
  var byText = window.__melodiaByText;
  var visible = window.__melodiaVisible;
  function inputs() {
    var list = [];
    var all = document.querySelectorAll("input");
    for (var i = 0; i < all.length; i++) if (visible(all[i])) list.push(all[i]);
    return list;
  }
  function setValue(el, v) {
    try {
      var desc = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value");
      if (desc && desc.set) desc.set.call(el, v); else el.value = v;
      el.dispatchEvent(new Event("input", { bubbles: true }));
      el.dispatchEvent(new Event("change", { bubbles: true }));
      el.blur();
      return true;
    } catch (e) { return false; }
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
  var tries = 0;
  function retry() {
    tries++;
    if (tries > 40) { report("fail", "没能在网页登录页找到手机号输入框"); return; }
    setTimeout(walk, 500);
  }
  function walk() {
    if (window.__melodiaHasVerify()) { report("captcha", ""); return; }
    var phoneEl = findPhone();
    if (!phoneEl) {
      var method = byText(/(手机号登录|短信验证码登录|验证码登录|手机登录)/);
      if (method) { method.click(); report("progress", "打开手机号登录"); }
      retry();
      return;
    }
    if (phoneEl.value !== PHONE) setValue(phoneEl, PHONE);
    var sendBtn = byText(/(获取验证码|发送验证码|点击获取|重新获取)/);
    if (!sendBtn) { retry(); return; }
    sendBtn.click();
    report("progress", "已点击获取验证码");
  }
  setTimeout(walk, 300);
})();
"""

// 点完之后的观察器：只读页面状态不再点击（重复点击会把验证弹窗重新触发），
// 验证期间顺手把验证框滚进视野，避免用户一进来看到的是空白登录页
private const val WATCHER_JS = """
(function () {
  // 重复注入只保留一个观察循环
  if (window.__melodiaWatching) return;
  window.__melodiaWatching = true;
  var report = window.__melodiaReport;
  var byText = window.__melodiaByText;
  var sawVerify = false;
  var checks = 0;
  function done(type, message) {
    window.__melodiaWatching = false;
    report(type, message);
  }
  function tick() {
    checks++;
    var verify = window.__melodiaHasVerify();
    if (verify) {
      if (!sawVerify) { sawVerify = true; report("captcha", ""); }
      try { if (verify.scrollIntoView) verify.scrollIntoView({ block: "center" }); } catch (e) {}
      if (checks < 240) setTimeout(tick, 500);
      return;
    }
    if (sawVerify) { done("verifygone", ""); return; }
    var sent = byText(/(重新获取|重发|已发送|\d+\s*[sS]\s*后)/);
    if (sent) { done("sent", (sent.innerText || "").trim()); return; }
    var err = byText(/(发送失败|操作频繁|请稍后|验证失败)/);
    if (err) { done("fail", (err.innerText || "").trim()); return; }
    // 发码流程里验证通过后页面会自己发，继续等；别的流程由 Kotlin 侧超时兜底
    if (checks < 240) setTimeout(tick, 500);
  }
  setTimeout(tick, 1200);
})();
"""

// 登录流程：填手机号 + 验证码再点登录按钮；滑块验证通过后会重新注入一次
private const val LOGIN_JS = """
(function () {
  var PHONE = "__PHONE__";
  var CODE = "__CODE__";
  var report = window.__melodiaReport;
  var byText = window.__melodiaByText;
  var visible = window.__melodiaVisible;
  function inputs() {
    var list = [];
    var all = document.querySelectorAll("input");
    for (var i = 0; i < all.length; i++) if (visible(all[i])) list.push(all[i]);
    return list;
  }
  function setValue(el, v) {
    try {
      var desc = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value");
      if (desc && desc.set) desc.set.call(el, v); else el.value = v;
      el.dispatchEvent(new Event("input", { bubbles: true }));
      el.dispatchEvent(new Event("change", { bubbles: true }));
      el.blur();
      return true;
    } catch (e) { return false; }
  }
  var tries = 0;
  function retry() {
    tries++;
    if (tries > 30) { report("fail", "没能在网页登录页填写验证码"); return; }
    setTimeout(walk, 500);
  }
  function walk() {
    if (window.__melodiaHasVerify()) { report("captcha", ""); return; }
    var list = inputs();
    if (list.length < 2) {
      var method = byText(/(手机号登录|短信验证码登录|验证码登录|手机登录)/);
      if (method) method.click();
      retry();
      return;
    }
    var phoneEl = list[0];
    for (var i = 0; i < list.length; i++) {
      var hint = (list[i].placeholder || "") + (list[i].name || "") + (list[i].id || "");
      if (list[i].type === "tel" || /手机|电话|phone|tel|mobile/i.test(hint)) { phoneEl = list[i]; break; }
    }
    setValue(phoneEl, PHONE);
    var codeEl = null;
    for (var j = 0; j < list.length; j++) {
      if (list[j] === phoneEl) continue;
      var h2 = (list[j].placeholder || "") + (list[j].name || "") + (list[j].id || "");
      if (/验证码|code|captcha|sms/i.test(h2)) { codeEl = list[j]; break; }
    }
    if (!codeEl) codeEl = list[1];
    setValue(codeEl, CODE);
    var btn = byText(/(立即登录|登录\/注册|登\s*录)/);
    if (!btn) { retry(); return; }
    btn.click();
    report("progress", "正在提交登录");
    report("submitted", "");
  }
  setTimeout(walk, 300);
})();
"""

/**
 * 用 WebView 驱动网易云网页登录页发码，成功后从 Cookie 里取 MUSIC_U。
 *
 * 平时窗口透明、移出屏幕、不可触摸，用户只看到我们自己的手机号登录界面；
 * 一旦网易要求行为验证（接口 -462），页面会弹滑块，这时把窗口缩小居中、压暗背景、恢复触摸，
 * 让用户就地拖一下滑块；验证完成后再缩回去，验证码由页面自己发出去。
 */
class AndroidSmsWebDriver(private val context: Context) : SmsWebDriver {

    private val main = Handler(Looper.getMainLooper())
    private val activity: Activity? = context.findActivity()

    private var dialog: Dialog? = null
    private var webView: WebView? = null
    private var pageReady = false
    private var pageQueued: (() -> Unit)? = null
    private var presented = false

    private var sendCallback: ((Result<Unit>) -> Unit)? = null
    private var sendProgress: ((String) -> Unit)? = null
    private var loginCallback: ((Result<String>) -> Unit)? = null
    private var loginPolling = false
    private var loginAttempts = 0
    private var timeoutGeneration = 0
    private var lastPhone = ""
    private var lastCode = ""
    private val timeouts = mutableListOf<Runnable>()

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

    override fun sendCode(phone: String, onProgress: (String) -> Unit, onResult: (Result<Unit>) -> Unit) {
        main.post {
            if (!ensurePage()) {
                onResult(Result.failure(fail("网页登录组件初始化失败")))
                return@post
            }
            settlePendingCallbacks()
            sendCallback = onResult
            sendProgress = onProgress
            onProgress("正在打开网易登录页…")
            schedule(SEND_TIMEOUT_MS) { failSend("网页登录页没有响应，请改用网页登录") }
            inject(DRIVER_JS.replace("__PHONE__", phone))
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
            lastPhone = phone
            lastCode = code
            loginAttempts = 1
            schedule(LOGIN_TIMEOUT_MS) {
                if (loginCallback != null) failLogin("网页登录超时，请重试或改用网页登录")
            }
            startCookiePolling()
            inject(LOGIN_JS.replace("__PHONE__", phone).replace("__CODE__", code))
        }
    }

    override fun release() {
        main.post {
            loginPolling = false
            main.removeCallbacks(loginPoller)
            pageQueued = null
            settlePendingCallbacks("已取消验证")
            runCatching { webView?.stopLoading() }
            runCatching { dialog?.dismiss() }
            runCatching { webView?.destroy() }
            dialog = null
            webView = null
            pageReady = false
            presented = false
        }
    }

    // ======================= 窗口显隐 =======================

    private fun ensurePage(): Boolean {
        val act = activity ?: return false
        if (webView != null) return true
        val wv = createWebView() ?: return false
        webView = wv
        val d = Dialog(act, android.R.style.Theme_Translucent_NoTitleBar)
        d.setContentView(wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        d.setCancelable(true)
        // 窗口显出来时按返回键视为放弃验证
        d.setOnCancelListener {
            if (presented) {
                hideWindow()
                failSend("已取消验证，验证码没有发出")
                failLogin("已取消验证")
            }
        }
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
            // 双保险：整块透明 + 移出屏幕，用户看不到也不会抢触摸
            lp.x = metrics.widthPixels
            lp.y = metrics.heightPixels
            lp.alpha = 0f
            w.attributes = lp
        }
        runCatching { d.show() }.onFailure {
            AppLogger.e(TAG, "网页登录窗口创建失败", it)
            webView = null
            return false
        }
        dialog = d
        wv.loadUrl(NeteaseEndpoints.LOGIN_URL)
        return true
    }

    /** 缩小居中 + 压暗背景 + 恢复触摸，用户就地拖滑块 */
    private fun showWindow() {
        val w = dialog?.window ?: return
        if (presented) return
        presented = true
        val metrics = context.resources.displayMetrics
        val lp = w.attributes
        lp.width = (metrics.widthPixels * 0.94f).toInt()
        lp.height = (metrics.heightPixels * 0.74f).toInt()
        lp.gravity = Gravity.CENTER
        lp.x = 0
        lp.y = 0
        lp.alpha = 1f
        w.attributes = lp
        w.setDimAmount(0.55f)
        w.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        w.setBackgroundDrawable(
            GradientDrawable().apply {
                cornerRadius = 24f * metrics.density
                setColor(Color.WHITE)
            }
        )
        evaluate("(function(){var el=window.__melodiaHasVerify&&window.__melodiaHasVerify();if(el&&el.scrollIntoView){el.scrollIntoView({block:'center'});}})();")
        sendProgress?.invoke("网易要求人机验证，请拖一下滑块")
    }

    /** 验证结束/取消后重新藏起来 */
    private fun hideWindow() {
        val w = dialog?.window ?: return
        if (!presented) return
        presented = false
        val metrics = context.resources.displayMetrics
        val lp = w.attributes
        lp.width = metrics.widthPixels
        lp.height = metrics.heightPixels
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = metrics.widthPixels
        lp.y = metrics.heightPixels
        lp.alpha = 0f
        w.attributes = lp
        w.setDimAmount(0f)
        w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        w.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
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
                    evaluate(PRELUDE_JS)
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
        if (pageReady) {
            main.postDelayed({ evaluate(script) }, 400)
        } else {
            pageQueued = { evaluate(script) }
            schedule(PAGE_LOAD_TIMEOUT_MS) {
                if (!pageReady) {
                    pageQueued = null
                    failSend("网页登录页加载超时，请改用网页登录")
                    failLogin("网页登录页加载超时，请改用网页登录")
                }
            }
        }
    }

    private fun evaluate(script: String) {
        runCatching { webView?.evaluateJavascript(script, null) }
    }

    private fun cancelTimeouts() {
        timeoutGeneration++
        timeouts.forEach { main.removeCallbacks(it) }
        timeouts.clear()
    }

    // 每次 schedule 都带上当时的代号，cancelTimeouts() 一加代号，旧回调即便已经在消息队列里也会作废
    private fun schedule(delayMs: Long, action: () -> Unit) {
        val token = timeoutGeneration
        val runnable = object : Runnable {
            override fun run() {
                if (token != timeoutGeneration) return
                timeouts.remove(this)
                action()
            }
        }
        timeouts.add(runnable)
        main.postDelayed(runnable, delayMs)
    }

    private fun onReport(payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val type = json.optString("type")
        val message = json.optString("message")
        AppLogger.d(TAG, "网页登录回报: type=$type, message=$message")
        when (type) {
            "progress" -> if (message.isNotBlank()) sendProgress?.invoke(message)
            "captcha" -> {
                // 网易要求行为验证：把窗口亮出来让用户拖滑块，之前的超时全部作废顺延
                if (sendCallback != null || loginCallback != null) {
                    cancelTimeouts()
                    showWindow()
                    schedule(CAPTCHA_TIMEOUT_MS) {
                        failSend("验证没有完成，验证码没有发出")
                        failLogin("验证没有完成")
                    }
                    evaluate(WATCHER_JS)
                } else {
                    hideWindow()
                }
            }
            "sent" -> {
                hideWindow()
                if (sendCallback != null) finishSend(Result.success(Unit))
            }
            "unknown" -> {
                hideWindow()
                if (sendCallback != null) finishSend(Result.success(Unit))
            }
            "fail" -> {
                hideWindow()
                val text = message.ifBlank { "网页登录页操作失败，请改用网页登录" }
                failSend(text)
                failLogin(text)
            }
            "verifygone" -> {
                // 用户拖完滑块：登录流程需要把手机号+验证码重新提交一次
                if (loginCallback != null && loginAttempts < 3) {
                    loginAttempts++
                    hideWindow()
                    cancelTimeouts()
                    schedule(LOGIN_TIMEOUT_MS) {
                        if (loginCallback != null) failLogin("网页登录超时，请重试或改用网页登录")
                    }
                    inject(LOGIN_JS.replace("__PHONE__", lastPhone).replace("__CODE__", lastCode))
                } else if (sendCallback == null) {
                    hideWindow()
                }
            }
            "submitted" -> {
                // 提交后再盯一会儿：登录时也常被要求拖滑块
                startCookiePolling()
                evaluate(WATCHER_JS)
            }
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
        sendProgress = null
        hideWindow()
        cb(result)
    }

    private fun finishLogin(result: Result<String>) {
        val cb = loginCallback ?: return
        loginCallback = null
        loginPolling = false
        main.removeCallbacks(loginPoller)
        hideWindow()
        CookieManager.getInstance().flush()
        cb(result)
    }

    private fun failSend(text: String) = finishSend(Result.failure(fail(text)))

    private fun failLogin(text: String) = finishLogin(Result.failure(fail(text)))

    private fun fail(text: String) = AppError.BizError(-1, text)

    // 上一轮没回调就再来一次时，先把旧的失败掉，保证每次调用都只回调一次
    private fun settlePendingCallbacks(reason: String = "上一次操作已被新的请求替换") {
        cancelTimeouts()
        sendCallback?.let { cb ->
            sendCallback = null
            sendProgress = null
            cb(Result.failure(fail(reason)))
        }
        loginCallback?.let { cb ->
            loginCallback = null
            loginPolling = false
            main.removeCallbacks(loginPoller)
            cb(Result.failure(fail(reason)))
        }
        hideWindow()
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
