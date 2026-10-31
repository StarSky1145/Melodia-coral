package com.lin0721.linmusic.feature.localmusic.data.lyrics

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// QQ 音乐加密二进制/Hex QRC 歌词解密与解压
object QrcDecoder {

    private val QRC_KEY = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null

        // 1. 如果本身是明文文本，直接返回
        val plain = runCatching { decodeLyricsBytes(bytes) }.getOrNull()
        if (plain != null && isPlainLyric(plain)) {
            return plain
        }

        // 2. 检查是否为十六进制编码文本
        val cipherBytes = if (isHexSequence(bytes)) {
            hexToBytes(String(bytes, Charsets.US_ASCII)) ?: bytes
        } else {
            bytes
        }

        // 3. Triple-DES 解密
        val decrypted = decrypt3Des(cipherBytes) ?: return null

        // 4. Zlib / Raw Inflate / Gzip 解压
        val decompressed = decompress(decrypted)
        val result = if (decompressed != null) {
            decodeLyricsBytes(decompressed)
        } else {
            decodeLyricsBytes(decrypted)
        }

        return result.takeIf { it.isNotBlank() && (it.contains('<') || it.contains('[')) }
    }

    private fun isPlainLyric(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.startsWith('<') && !trimmed.startsWith('[')) return false
        // 排除恰好以 '[' 开头但主体全为十六进制数字的偶然情况
        return trimmed.contains('\n') || trimmed.contains("Lyric") || trimmed.contains("]")
    }

    private fun decrypt3Des(data: ByteArray): ByteArray? = runCatching {
        val alignedLen = (data.size / 8) * 8
        if (alignedLen == 0) return null
        val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
        val keySpec = SecretKeySpec(QRC_KEY, "DESede")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        cipher.doFinal(data, 0, alignedLen)
    }.getOrNull()

    private fun decompress(data: ByteArray): ByteArray? {
        // 标准 Zlib
        runCatching {
            InflaterInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        // Raw Inflate（无 Zlib 包头）
        runCatching {
            val inflater = Inflater(true)
            try {
                InflaterInputStream(ByteArrayInputStream(data), inflater).use { it.readBytes() }
            } finally {
                inflater.end()
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        // Gzip
        runCatching {
            GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        return null
    }

    private fun isHexSequence(bytes: ByteArray): Boolean {
        if (bytes.size < 16) return false
        var hexCharCount = 0
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (c.isWhitespace()) continue
            if (c !in '0'..'9' && c !in 'a'..'f' && c !in 'A'..'F') return false
            hexCharCount++
        }
        return hexCharCount >= 16 && hexCharCount % 2 == 0
    }

    private fun hexToBytes(hex: String): ByteArray? = runCatching {
        val clean = hex.filterNot { it.isWhitespace() }
        if (clean.length % 2 != 0) return null
        val result = ByteArray(clean.length / 2)
        for (i in clean.indices step 2) {
            result[i / 2] = clean.substring(i, i + 2).toInt(16).toByte()
        }
        result
    }.getOrNull()
}
