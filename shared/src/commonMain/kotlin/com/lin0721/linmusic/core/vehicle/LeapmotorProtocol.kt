package com.lin0721.linmusic.core.vehicle

// 零跑车机方向盘 / 媒体源协议。均为实机逆向所得，非官方文档，固件升级后可能变化
object LeapmotorProtocol {
    // 车机把方控按键发成这条广播，extras["action"] 为 Int
    const val ACTION_STEERING = "com.leapmotor.steerwheelcontrol.media"
    const val EXTRA_STEERING_ACTION = "action"

    // 声明"当前媒体源"，车机按媒体源分发方控；不声明则按键会同时打到原车音乐
    const val ACTION_MEDIA_INFO = "com.leapmotor.transfer.MEDIA.INFO"
    const val EXTRA_MEDIA_INFO = "info"

    // 车机当前前台 Activity，形如 "包名/类名"
    const val SETTING_TOP_ACTIVITY = "display_0_top_activity"

    // Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND 是隐藏常量
    const val FLAG_RECEIVER_INCLUDE_BACKGROUND = 0x01000000
    const val FLAG_RECEIVER_FOREGROUND = 0x10000000

    // 车机有两条取键路径会同时触发，同键窗口内只认第一次
    const val DEDUP_WINDOW_MS = 400L
    const val CLAIM_HEARTBEAT_MS = 1000L

    // 厂商原版写法：内层 type 固定 15；键名 imagUrl 少一个 e，均不可"修正"
    const val MEDIA_INFO_DATA_TYPE = 15
}

enum class SteeringKey(val code: Int) {
    PREVIOUS(1),
    NEXT(4),
    PLAY_PAUSE(7);

    companion object {
        fun fromCode(code: Int): SteeringKey? = entries.firstOrNull { it.code == code }
    }
}

// 同键在窗口内重复到达视为同一次按压；以最近一次被放行的时刻为基准，长按不会被永久吞掉
class SteeringKeyDebouncer(private val windowMs: Long = LeapmotorProtocol.DEDUP_WINDOW_MS) {
    private var lastKey: SteeringKey? = null
    private var lastAcceptedAtMs = 0L

    fun accept(key: SteeringKey, nowMs: Long): Boolean {
        if (key == lastKey && nowMs - lastAcceptedAtMs <= windowMs) return false
        lastKey = key
        lastAcceptedAtMs = nowMs
        return true
    }
}

// 前台门：只有自己在前台，或正在播放（后台放歌、前台开导航）时才声明媒体源；
// 既不在前台又没在播时不推，避免压住其他 App 的声明
fun shouldClaimMediaSource(selfOnTop: Boolean, playWhenReady: Boolean): Boolean =
    selfOnTop || playWhenReady

fun isSelfOnTop(topActivity: String?, packageName: String): Boolean =
    topActivity?.startsWith("$packageName/") == true

fun buildMediaInfoJson(title: String, artist: String, playing: Boolean): String {
    val t = escapeJson(title)
    val a = escapeJson(artist)
    val state = if (playing) 1 else 0
    return """{"type":"music","data":{"type":${LeapmotorProtocol.MEDIA_INFO_DATA_TYPE},""" +
        """"name":"$t","title":"$t","artist":"$a","duration":0,"imagUrl":"","state":$state}}"""
}

private fun escapeJson(s: String): String = buildString(s.length + 8) {
    for (c in s) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> append(c)
        }
    }
}
