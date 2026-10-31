package com.lin0721.linmusic.feature.localmusic.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class LocalLyricsFilesTest {

    @Test
    fun `UTF-8 歌词去掉 BOM`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "[00:01.00]你好".toByteArray(Charsets.UTF_8)
        assertEquals("[00:01.00]你好", decodeLyricsBytes(bytes))
    }

    @Test
    fun `非法 UTF-8 按 GBK 解码`() {
        val bytes = "[00:01.00]冬之钟".toByteArray(Charset.forName("GBK"))
        assertEquals("[00:01.00]冬之钟", decodeLyricsBytes(bytes))
    }

    @Test
    fun `QrcDecoder解密并解压标准3DES加密数据`() {
        val plainText = "[0,3000]你好(0,1000)世界(1000,2000)"
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.DeflaterOutputStream(baos).use { it.write(plainText.toByteArray(Charsets.UTF_8)) }
        val compressed = baos.toByteArray()

        // 补齐 8 字节对齐
        val remainder = compressed.size % 8
        val padding = if (remainder == 0) 0 else 8 - remainder
        val padded = compressed.copyOf(compressed.size + padding)

        val cipher = javax.crypto.Cipher.getInstance("DESede/ECB/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec("!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.UTF_8), "DESede")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(padded)

        val decoded = QrcDecoder.decode(encrypted)
        assertEquals(plainText, decoded)
    }
}
