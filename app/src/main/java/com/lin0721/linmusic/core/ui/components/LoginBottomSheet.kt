package com.lin0721.linmusic.core.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.common.BitMatrix
import com.lin0721.linmusic.core.auth.AndroidSmsWebDriver
import com.lin0721.linmusic.core.auth.LoginViewModel
import com.lin0721.linmusic.core.auth.QrLoginState
import com.lin0721.linmusic.core.auth.SmsLoginState
import com.lin0721.linmusic.core.auth.SmsWebDriver
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.ContentSwitchDurationMs
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import com.lin0721.linmusic.core.ui.theme.SurfaceLight
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.IsDarkStyle
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.uiTween
import org.koin.androidx.compose.koinViewModel
import com.lin0721.linmusic.core.ui.theme.ContentPrimary
import com.lin0721.linmusic.core.ui.theme.styledCorners
import com.lin0721.linmusic.core.ui.theme.OnAccent

private enum class LoginMode { PHONE, CHOICE, QR, COOKIE }

// PHONE（验证码登录）为根，CHOICE 为"其他登录方式"，QR/COOKIE 再下一层，用于 AnimatedContent 判定滑动方向
private fun LoginMode.depth(): Int = when (this) {
    LoginMode.PHONE -> 0
    LoginMode.CHOICE -> 1
    LoginMode.QR, LoginMode.COOKIE -> 2
}

// 返回按钮回到上一层
private fun LoginMode.parent(): LoginMode = when (this) {
    LoginMode.PHONE, LoginMode.CHOICE -> LoginMode.PHONE
    LoginMode.QR, LoginMode.COOKIE -> LoginMode.CHOICE
}

/**
 * 登录底部弹窗
 * 默认手机号验证码登录；网页登录跳全屏 WebView，二维码/Cookie 登录在弹窗内内联完成
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginBottomSheet(
    onDismiss: () -> Unit,
    onWebLogin: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    val motion = LocalUiMotion.current
    val viewModel: LoginViewModel = koinViewModel()
    var mode by remember { mutableStateOf(LoginMode.PHONE) }

    // 发码走隐藏的网页登录页：平时不可见，只有网易要求滑块验证时才把那一小块窗口亮出来
    val context = LocalContext.current
    val smsDriver = remember { AndroidSmsWebDriver(context) }
    val smsWebDriver: SmsWebDriver? = remember(smsDriver) { if (smsDriver.available) smsDriver else null }

    // 弹窗整体离开组合时兜底停止轮询，覆盖点击遮罩/手势关闭等未经返回按钮的路径
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetQrState()
            smsDriver.release()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            viewModel.resetQrState()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = if (IsDarkStyle) SurfaceDark else BackgroundDark,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MelodiaSpacing.lg)
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = mode != LoginMode.PHONE,
                // 只淡入淡出会导致这一行的高度在退场时一直占位，到组件真正移除才瞬间塌陷；
                // 叠加 expand/shrink 让高度也跟着动画走，避免收尾那一下顿挫
                enter = fadeIn(uiTween(ContentSwitchDurationMs, easing = FastOutSlowInEasing)) +
                    expandVertically(uiTween(ContentSwitchDurationMs, easing = FastOutSlowInEasing)),
                exit = fadeOut(uiTween(ContentSwitchDurationMs, easing = FastOutSlowInEasing)) +
                    shrinkVertically(uiTween(ContentSwitchDurationMs, easing = FastOutSlowInEasing))
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MelodiaIconButton(onClick = {
                        viewModel.resetQrState()
                        mode = mode.parent()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = ContentPrimary)
                    }
                }
            }

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    val offsetSpec = motion.tween<androidx.compose.ui.unit.IntOffset>(ContentSwitchDurationMs, easing = FastOutSlowInEasing)
                    val alphaSpec = motion.tween<Float>(ContentSwitchDurationMs, easing = FastOutSlowInEasing)
                    val transform = if (targetState.depth() > initialState.depth()) {
                        // 前进：新内容从右滑入，旧内容向左滑出
                        (slideInHorizontally(offsetSpec) { it } + fadeIn(alphaSpec)) togetherWith
                            (slideOutHorizontally(offsetSpec) { -it } + fadeOut(alphaSpec))
                    } else {
                        // 返回：方向相反
                        (slideInHorizontally(offsetSpec) { -it } + fadeIn(alphaSpec)) togetherWith
                            (slideOutHorizontally(offsetSpec) { it } + fadeOut(alphaSpec))
                    }
                    // 裁剪到动画中的实际尺寸，否则收缩方向（如 QR → CHOICE）旧内容按原尺寸绘制，
                    // 跟弹窗实际高度对不上，切换完成瞬间会有一次纠正造成的顿挫
                    transform.using(SizeTransform(clip = true))
                },
                label = "LoginModeContent"
            ) { targetMode ->
                // AnimatedContent 的内容槽本身是 Box，各态内容需要各自的 Column 才能纵向排列
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (targetMode) {
                        LoginMode.PHONE -> PhoneLoginContent(
                            viewModel = viewModel,
                            webDriver = smsWebDriver,
                            onLoginSuccess = onLoginSuccess,
                            onOtherMethods = { mode = LoginMode.CHOICE },
                            onWebLogin = onWebLogin
                        )
                        LoginMode.CHOICE -> LoginChoiceContent(
                            onWebLogin = onWebLogin,
                            onQrLogin = {
                                mode = LoginMode.QR
                                viewModel.startQrLogin(onLoginSuccess)
                            },
                            onCookieLogin = { mode = LoginMode.COOKIE }
                        )
                        LoginMode.QR -> QrLoginContent(viewModel = viewModel, onLoginSuccess = onLoginSuccess)
                        LoginMode.COOKIE -> CookieLoginContent(
                            onSubmit = { raw -> viewModel.submitCookieLogin(raw, onLoginSuccess) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 手机号验证码登录（默认）
 */
@Composable
private fun PhoneLoginContent(
    viewModel: LoginViewModel,
    webDriver: SmsWebDriver?,
    onLoginSuccess: (String) -> Unit,
    onOtherMethods: () -> Unit,
    onWebLogin: () -> Unit
) {
    val state by viewModel.smsState.collectAsStateWithLifecycle()
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(styledCorners(12.dp))
                .background(NeteaseRed),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Smartphone, contentDescription = null, tint = OnAccent, modifier = Modifier.size(22.dp))
        }
        Text(
            text = "你好，\n今天想听点什么？",
            fontSize = 26.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ContentPrimary,
            modifier = Modifier.padding(top = MelodiaSpacing.lg)
        )
        Text(
            text = "用网易云音乐账号绑定的手机号登录",
            fontSize = 13.sp,
            color = TextGray,
            modifier = Modifier.padding(top = MelodiaSpacing.sm, bottom = MelodiaSpacing.lg)
        )

        UnderlinedField(
            value = phone,
            onValueChange = { input ->
                phone = input.filter { it.isDigit() }.take(11)
                viewModel.clearSmsError()
            },
            placeholder = "手机号",
            keyboardType = KeyboardType.Phone,
            leading = { Text("+86", fontSize = 16.sp, color = TextGray) }
        )
        Spacer(Modifier.height(MelodiaSpacing.sm))
        UnderlinedField(
            value = code,
            onValueChange = { input ->
                code = input.filter { it.isDigit() }.take(6)
                viewModel.clearSmsError()
            },
            placeholder = "验证码",
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
            letterSpacing = true,
            trailing = {
                ResendButton(
                    state = state,
                    onClick = { viewModel.sendSmsCode(phone, webDriver = webDriver) }
                )
            }
        )

        val hint = when {
            state.error != null -> state.error
            state.sending && state.status != null -> state.status
            state.sentTo != null && state.resendCountdown > 0 -> "验证码已发送至 ${state.sentTo!!.maskPhone()}"
            else -> null
        }
        Text(
            text = hint.orEmpty(),
            fontSize = 12.sp,
            color = when {
                state.error != null -> MaterialTheme.colorScheme.error
                else -> TextGray
            },
            modifier = Modifier.padding(top = MelodiaSpacing.sm).heightIn(min = 18.dp)
        )
        // 网易发码必须过人机验证，本地发不出去时直接把用户送去官方页面
        if (state.error != null) {
            MelodiaTextButton(
                onClick = {
                    WebLoginPrefill.phone = phone.trim()
                    onWebLogin()
                }
            ) {
                Text("改用网页登录", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeteaseRed)
            }
        }

        MelodiaButton(
            onClick = { viewModel.loginWithSms(phone, code, onLoginSuccess, webDriver = webDriver) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MelodiaSpacing.md)
                .height(52.dp),
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(containerColor = NeteaseRed)
        ) {
            if (state.loggingIn) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = OnAccent, strokeWidth = 2.dp)
            } else {
                Text("登录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnAccent)
            }
        }

        MelodiaTextButton(
            onClick = onOtherMethods,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = MelodiaSpacing.sm)
        ) {
            Text("其他登录方式", fontSize = 14.sp, color = TextGray)
        }
        Text(
            text = "验证码由网易云发送，遇到人机验证时会就地弹出滑块；登录凭据只保存在本机",
            fontSize = 11.sp,
            color = TextGray.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = MelodiaSpacing.xs)
        )
    }
}

// 获取验证码 / 倒计时 / 发送中
@Composable
private fun ResendButton(state: SmsLoginState, onClick: () -> Unit) {
    val counting = state.resendCountdown > 0
    val label = when {
        state.sending -> "发送中…"
        counting -> "${state.resendCountdown}s 后重发"
        state.sentTo != null -> "重新获取"
        else -> "获取验证码"
    }
    MelodiaTextButton(onClick = onClick, enabled = !state.sending && !counting) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (state.sending || counting) TextGray else NeteaseRed
        )
    }
}

// 下划线样式的单行输入框
@Composable
private fun UnderlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction = ImeAction.Next,
    letterSpacing: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(MelodiaSpacing.md))
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 16.sp, color = TextGray.copy(alpha = 0.7f))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = ContentPrimary,
                        fontSize = 16.sp,
                        letterSpacing = if (letterSpacing && value.isNotEmpty()) 6.sp else 0.sp
                    ),
                    cursorBrush = SolidColor(NeteaseRed),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailing != null) trailing()
        }
        HorizontalDivider(color = ContentPrimary.copy(alpha = 0.15f))
    }
}

// 138****8000
private fun String.maskPhone(): String =
    if (length == 11) "${take(3)}****${takeLast(4)}" else this

/**
 * 方式选择态：网页登录 / 二维码登录 / Cookie 登录
 */
@Composable
private fun LoginChoiceContent(
    onWebLogin: () -> Unit,
    onQrLogin: () -> Unit,
    onCookieLogin: () -> Unit
) {
    Text(
        text = "其他登录方式",
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        color = ContentPrimary,
        modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
    )
    Text(
        text = "验证码收不到或被拦截时，可以扫码或用网页登录",
        fontSize = 13.sp,
        color = TextGray,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = MelodiaSpacing.xl)
    )

    LoginOptionButton(text = "二维码登录", icon = Icons.Default.QrCode, isPrimary = true, onClick = onQrLogin)
    Spacer(modifier = Modifier.height(MelodiaSpacing.sm))
    LoginOptionButton(text = "网页登录", icon = Icons.Default.Language, isPrimary = false, onClick = onWebLogin)
    Spacer(modifier = Modifier.height(MelodiaSpacing.sm))
    LoginOptionButton(text = "Cookie 登录", icon = Icons.Default.ContentPaste, isPrimary = false, onClick = onCookieLogin)
}

/**
 * 二维码登录内联态：展示二维码图片 + 轮询状态文案
 */
@Composable
private fun QrLoginContent(
    viewModel: LoginViewModel,
    onLoginSuccess: (String) -> Unit
) {
    val state by viewModel.qrState.collectAsStateWithLifecycle()

    Text(
        text = "二维码登录",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = ContentPrimary,
        modifier = Modifier.padding(bottom = MelodiaSpacing.lg)
    )

    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(styledCorners(16.dp))
            // 二维码底板必须是白色，深浅风格都一样，否则扫码识别率下降
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        when (val s = state) {
            is QrLoginState.WaitingScan -> {
                val qrImage = remember(s.qrMatrix) { s.qrMatrix.toImageBitmap() }
                Image(
                    bitmap = qrImage,
                    contentDescription = "登录二维码",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            }
            is QrLoginState.WaitingConfirm -> {
                val qrImage = remember(s.qrMatrix) { s.qrMatrix.toImageBitmap() }
                Image(
                    bitmap = qrImage,
                    contentDescription = "登录二维码",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .alpha(0.3f)
                )
                CircularProgressIndicator(color = NeteaseRed)
            }
            // 过期或出错时不再转圈，避免看起来像还在加载
            QrLoginState.Expired, is QrLoginState.Error -> Icon(
                Icons.Default.QrCode,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.2f),
                modifier = Modifier.size(72.dp)
            )
            else -> CircularProgressIndicator(color = NeteaseRed)
        }
    }

    Spacer(modifier = Modifier.height(MelodiaSpacing.lg))

    val statusText = when (val s = state) {
        QrLoginState.Idle, QrLoginState.Loading -> "正在生成二维码..."
        is QrLoginState.WaitingScan -> "请使用网易云音乐App扫码登录"
        is QrLoginState.WaitingConfirm -> "扫描成功，请在手机上确认登录"
        QrLoginState.Expired -> "二维码已过期"
        is QrLoginState.Error -> s.message
    }
    Text(text = statusText, fontSize = 14.sp, color = TextGray, textAlign = TextAlign.Center)

    if (state is QrLoginState.Expired || state is QrLoginState.Error) {
        Spacer(modifier = Modifier.height(MelodiaSpacing.md))
        MelodiaTextButton(onClick = { viewModel.startQrLogin(onLoginSuccess) }) {
            Text("点击刷新", color = NeteaseRed, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Cookie 登录内联态
 */
@Composable
private fun CookieLoginContent(onSubmit: (String) -> Boolean) {
    var cookieInput by remember { mutableStateOf("") }
    // 校验失败提示放在弹窗内联展示：ModalBottomSheet 是独立 Window，全局 ToastManager 的提示会被它挡住看不见
    var errorText by remember { mutableStateOf<String?>(null) }

    Text(
        text = "Cookie 登录",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = ContentPrimary,
        modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
    )
    Text(
        text = "粘贴Cookie 字符串",
        fontSize = 13.sp,
        color = TextGray,
        modifier = Modifier.padding(bottom = MelodiaSpacing.md)
    )
    PlaceholderTextField(
        value = cookieInput,
        onValueChange = {
            cookieInput = it
            errorText = null
        },
        placeholder = "MUSIC_U=xxxxxx",
        singleLine = false,
        minLines = 3
    )
    if (errorText != null) {
        Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
        Text(
            text = errorText.orEmpty(),
            fontSize = 12.sp,
            color = NeteaseRed,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(modifier = Modifier.height(MelodiaSpacing.lg))
    MelodiaButton(
        onClick = {
            if (!onSubmit(cookieInput)) {
                errorText = "格式不正确，请粘贴完整 Cookie 字符串或 MUSIC_U 的值"
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = styledCorners(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NeteaseRed),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp)
    ) {
        Text("确认登录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnAccent)
    }
}

/**
 * 登录选项按钮
 */
@Composable
private fun LoginOptionButton(
    text: String,
    icon: ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    if (isPrimary) {
        MelodiaButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = styledCorners(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeteaseRed
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 2.dp
            )
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = OnAccent
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = OnAccent
            )
        }
    } else {
        MelodiaOutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = styledCorners(16.dp),
            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(SurfaceLight)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = ContentPrimary
            )
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = TextGray
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = ContentPrimary
            )
        }
    }
}

// 一次性 setPixels 批量写入
private fun BitMatrix.toImageBitmap(): ImageBitmap {
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val rowOffset = y * width
        for (x in 0 until width) {
            pixels[rowOffset + x] = if (get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}
