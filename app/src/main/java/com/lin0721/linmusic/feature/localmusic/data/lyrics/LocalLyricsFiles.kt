package com.lin0721.linmusic.feature.localmusic.data.lyrics

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction


private val GBK: Charset = Charset.forName("GBK")

// 优先 UTF-8（去 BOM），解码失败再按 GBK：老歌词文件大多是 GBK
fun decodeLyricsBytes(bytes: ByteArray): String {
    val hasBom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    val content = if (hasBom) bytes.copyOfRange(3, bytes.size) else bytes
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(content))
            .toString()
    } catch (e: CharacterCodingException) {
        String(content, GBK)
    }
}

// 统一本地歌词解码：针对 .qrc 优先尝试解密，若解码为空或失败则兜底尝试 QRC 解密
fun decodeLocalLyrics(bytes: ByteArray, fileName: String? = null): String? {
    if (bytes.isEmpty()) return null
    if (fileName != null && fileName.endsWith(".qrc", ignoreCase = true)) {
        val qrcDecoded = QrcDecoder.decode(bytes)
        if (!qrcDecoded.isNullOrBlank()) return qrcDecoded
    }
    val plain = decodeLyricsBytes(bytes)
    if (plain.isNotBlank()) {
        return plain
    }
    return QrcDecoder.decode(bytes)
}
