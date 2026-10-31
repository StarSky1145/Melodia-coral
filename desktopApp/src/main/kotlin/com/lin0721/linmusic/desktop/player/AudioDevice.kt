package com.lin0721.linmusic.desktop.player

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// mpv 的“跟随系统默认设备”取值
const val AUTO_AUDIO_DEVICE = "auto"

enum class AudioDeviceKind { SYSTEM_DEFAULT, SPEAKER, HEADPHONES, DISPLAY }

// mpv 只给设备名与描述；描述形如“扬声器 (Realtek High Definition Audio)”，括号内作副标题
data class AudioDevice(val name: String, val description: String, val kind: AudioDeviceKind) {
    val isSystemDefault: Boolean get() = name == AUTO_AUDIO_DEVICE

    val title: String
        get() = if (isSystemDefault) "系统默认" else splitDescription().first

    val subtitle: String?
        get() = if (isSystemDefault) null else splitDescription().second

    private fun splitDescription(): Pair<String, String?> {
        val open = description.indexOf(" (")
        if (open <= 0 || !description.endsWith(")")) return description to null
        val detail = description.substring(open + 2, description.length - 1).trim()
        return description.substring(0, open).trim() to detail.ifEmpty { null }
    }
}

// 桌面端音频输出设备的读取与切换，由 mpv 控制器实现
interface AudioOutputControl {
    val audioDevices: StateFlow<List<AudioDevice>>
    val audioDevice: StateFlow<String>
    fun refreshAudioDevices()
    fun setAudioDevice(name: String)
}

private const val WASAPI_PREFIX = "wasapi/"

private val HEADPHONE_KEYWORDS = listOf("耳机", "头戴", "headphone", "headset", "airpods", "buds", "bluetooth", "蓝牙")
private val DISPLAY_KEYWORDS = listOf("hdmi", "displayport", "显示器", "monitor", "display audio", "nvidia high definition", "amd high definition")

internal fun classifyAudioDevice(name: String, description: String): AudioDeviceKind {
    if (name == AUTO_AUDIO_DEVICE) return AudioDeviceKind.SYSTEM_DEFAULT
    val text = description.lowercase()
    return when {
        HEADPHONE_KEYWORDS.any { it in text } -> AudioDeviceKind.HEADPHONES
        DISPLAY_KEYWORDS.any { it in text } -> AudioDeviceKind.DISPLAY
        else -> AudioDeviceKind.SPEAKER
    }
}

// 解析 mpv 的 audio-device-list（JSON 数组）；格式不符或缺少 name 的条目跳过，系统默认置顶
internal fun parseAudioDevices(json: String): List<AudioDevice> {
    val array = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonArray ?: return emptyList()
    val devices = array.mapNotNull { element ->
        val entry = element as? JsonObject ?: return@mapNotNull null
        val name = (entry["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val description = (entry["description"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: name
        AudioDevice(name, description, classifyAudioDevice(name, description))
    }.distinctBy { it.name }
    // 列表里混有其他后端的泛称条目（如 openal），Windows 下只保留 WASAPI 设备；没有 WASAPI 时才全部保留
    val wasapi = devices.filter { it.isSystemDefault || it.name.startsWith(WASAPI_PREFIX) }
    val usable = if (wasapi.any { !it.isSystemDefault }) wasapi else devices
    return usable.sortedByDescending { it.isSystemDefault }
}
