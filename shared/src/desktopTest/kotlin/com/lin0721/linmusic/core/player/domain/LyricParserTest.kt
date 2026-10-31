package com.lin0721.linmusic.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricParserTest {

    // ======================= LRC 解析 =======================

    @Test
    fun `解析标准两位毫秒的LRC行`() {
        val lines = LyricParser.parseLrc("[00:12.34]Hello world")
        assertEquals(1, lines.size)
        assertEquals(12340L, lines[0].timeMs) // 12s + 34*10ms
        assertEquals("Hello world", lines[0].text)
    }

    @Test
    fun `解析三位毫秒的LRC行`() {
        val lines = LyricParser.parseLrc("[00:12.340]Hello world")
        assertEquals(1, lines.size)
        assertEquals(12340L, lines[0].timeMs)
    }

    @Test
    fun `LRC支持冒号或点号作为毫秒分隔符`() {
        val lines = LyricParser.parseLrc("[01:02:500]Colon separated")
        assertEquals(1, lines.size)
        assertEquals(62500L, lines[0].timeMs) // 1*60000 + 2*1000 + 500
    }

    @Test
    fun `LRC多行按时间戳排序`() {
        val text = """
            [00:20.00]Second
            [00:10.00]First
        """.trimIndent()
        val lines = LyricParser.parseLrc(text)
        assertEquals(2, lines.size)
        assertEquals("First", lines[0].text)
        assertEquals("Second", lines[1].text)
    }

    @Test
    fun `LRC空文本行被跳过`() {
        val text = "[00:10.00]\n[00:20.00]Real line"
        val lines = LyricParser.parseLrc(text)
        assertEquals(1, lines.size)
        assertEquals("Real line", lines[0].text)
    }

    @Test
    fun `LRC不匹配格式的行被忽略`() {
        val text = "not a lyric line\n[00:10.00]Valid line"
        val lines = LyricParser.parseLrc(text)
        assertEquals(1, lines.size)
        assertEquals("Valid line", lines[0].text)
    }

    @Test
    fun `LRC空字符串返回空列表`() {
        assertTrue(LyricParser.parseLrc("").isEmpty())
    }

    @Test
    fun `一行多个时间标签各展开成一行`() {
        val lines = LyricParser.parseLrc("[00:12.00][01:30.50]副歌\n[00:20.00]主歌")
        assertEquals(listOf(12_000L, 20_000L, 90_500L), lines.map { it.timeMs })
        assertEquals(listOf("副歌", "主歌", "副歌"), lines.map { it.text })
    }

    @Test
    fun `非时间标签的元信息行被忽略`() {
        val lines = LyricParser.parseLrc("[ti:Winter Bells]\n[ar:茶太]\n[00:01.00]正文")
        assertEquals(1, lines.size)
        assertEquals("正文", lines[0].text)
    }

    // ======================= YRC 逐字解析 =======================

    @Test
    fun `解析单行YRC并计算逐字相对偏移`() {
        val yrc = "[0,3000](0,500,0)Hello(500,500,0)World"
        val lines = LyricParser.parseYrc(yrc)
        assertEquals(1, lines.size)
        val line = lines[0]
        assertEquals(0L, line.timeMs)
        assertEquals(3000L, line.durationMs)
        assertEquals("HelloWorld", line.text)
        assertEquals(2, line.words.size)
        assertEquals("Hello", line.words[0].text)
        assertEquals(0L, line.words[0].startOffsetMs)
        assertEquals(500L, line.words[0].durationMs)
        assertEquals("World", line.words[1].text)
        assertEquals(500L, line.words[1].startOffsetMs) // 500(绝对时间) - 0(行起始时间)
    }

    @Test
    fun `YRC单字起始时间相对行起始时间做偏移换算`() {
        // 行起始时间非0时，字词的相对偏移应为 绝对时间-行起始时间
        val yrc = "[1000,2000](1000,300,0)Word1(1300,300,0)Word2"
        val lines = LyricParser.parseYrc(yrc)
        assertEquals(1, lines.size)
        assertEquals(0L, lines[0].words[0].startOffsetMs)
        assertEquals(300L, lines[0].words[1].startOffsetMs)
    }

    @Test
    fun `YRC多行按时间戳排序`() {
        val yrc = "[2000,1000](2000,500,0)Second\n[0,1000](0,500,0)First"
        val lines = LyricParser.parseYrc(yrc)
        assertEquals(2, lines.size)
        assertEquals("First", lines[0].text)
        assertEquals("Second", lines[1].text)
    }

    @Test
    fun `YRC空白行与不匹配行被跳过`() {
        val yrc = "\n   \nnot a yrc line\n[0,1000](0,500,0)Valid"
        val lines = LyricParser.parseYrc(yrc)
        assertEquals(1, lines.size)
        assertEquals("Valid", lines[0].text)
    }

    @Test
    fun `YRC空字符串返回空列表`() {
        assertTrue(LyricParser.parseYrc("").isEmpty())
    }

    // ======================= 本地歌词（增强型 LRC + 同时间戳译文） =======================

    @Test
    fun `本地歌词解析增强型LRC逐字标签`() {
        val text = "[00:13.463] <00:13.463>Yeah <00:14.095>I'm <00:14.258>gonna<00:14.500>"
        val line = LyricParser.parseLocal(text).single()
        assertEquals(13_463L, line.timeMs)
        assertEquals("Yeah I'm gonna", line.text)
        assertEquals(listOf("Yeah ", "I'm ", "gonna"), line.words.map { it.text })
        assertEquals(listOf(0L, 632L, 795L), line.words.map { it.startOffsetMs })
        assertEquals(listOf(632L, 163L, 242L), line.words.map { it.durationMs })
        assertEquals(1_037L, line.durationMs)
    }

    @Test
    fun `逐字标签间仅有空白时并入前一个词`() {
        val line = LyricParser.parseLocal("[00:00.000] <00:00.000>Old<00:00.065> <00:00.087>Town<00:00.174>").single()
        assertEquals(listOf("Old ", "Town"), line.words.map { it.text })
        assertEquals(87L, line.words[1].startOffsetMs)
        assertEquals(87L, line.words[1].durationMs)
    }

    @Test
    fun `缺少结束标签时最后一个词时长为0`() {
        val line = LyricParser.parseLocal("[00:01.000]<00:01.000>Hi <00:01.500>there").single()
        assertEquals(0L, line.words.last().durationMs)
        assertEquals("Hi there", line.text)
    }

    @Test
    fun `同时间戳的第二行作为译文`() {
        val text = "[00:13.190]Yeah, take my horse\n[00:13.190]我要带着我的马\n[00:17.420]ride\n[00:17.420]直到筋疲力竭"
        val lines = LyricParser.parseLocal(text)
        assertEquals(2, lines.size)
        assertEquals("Yeah, take my horse", lines[0].text)
        assertEquals("我要带着我的马", lines[0].translation)
        assertEquals("直到筋疲力竭", lines[1].translation)
    }

    @Test
    fun `逐字原文与同时间戳译文合并`() {
        val text = "[00:13.463] <00:13.463>Yeah <00:14.095>I'm<00:14.500>\n[00:13.463]我要在老城小路上"
        val line = LyricParser.parseLocal(text).single()
        assertEquals("Yeah I'm", line.text)
        assertEquals(2, line.words.size)
        assertEquals("我要在老城小路上", line.translation)
    }

    @Test
    fun `同时间戳第三行保留为独立行`() {
        val lines = LyricParser.parseLocal("[00:10.00]a\n[00:10.00]b\n[00:10.00]c")
        assertEquals(2, lines.size)
        assertEquals("b", lines[0].translation)
        assertEquals("c", lines[1].text)
    }

    @Test
    fun `本地歌词普通LRC行为与parseLrc一致`() {
        val text = "[ti:Winter Bells]\n[00:20.00]主歌\n[00:12.00][01:30.50]副歌"
        val lines = LyricParser.parseLocal(text)
        assertEquals(listOf(12_000L, 20_000L, 90_500L), lines.map { it.timeMs })
        assertTrue(lines.all { it.words.isEmpty() && it.translation == null })
    }

    @Test
    fun `本地歌词空字符串返回空列表`() {
        assertTrue(LyricParser.parseLocal("").isEmpty())
    }

    // ======================= QRC 逐字与多格式自动嗅探 =======================

    @Test
    fun `QRC逐字解析纯文本行`() {
        val qrc = "[1000,3000]你好(1000,1000)世界(2000,2000)"
        val lines = QrcLyricParser.parse(qrc)
        assertEquals(1, lines.size)
        val line = lines[0]
        assertEquals(1000L, line.timeMs)
        assertEquals(3000L, line.durationMs)
        assertEquals("你好世界", line.text)
        assertEquals(2, line.words.size)
        assertEquals("你好", line.words[0].text)
        assertEquals(0L, line.words[0].startOffsetMs)
        assertEquals(1000L, line.words[0].durationMs)
        assertEquals("世界", line.words[1].text)
        assertEquals(1000L, line.words[1].startOffsetMs)
        assertEquals(2000L, line.words[1].durationMs)
    }

    @Test
    fun `QRC解析XML包裹内容与实体反转义`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <QrcInfos>
                <LyricInfo>
                    <Lyric_1 LyricType="1" LyricContent="[0,4000]Rock &amp; Roll(0,2000)&apos;s(2000,2000)"/>
                </LyricInfo>
            </QrcInfos>
        """.trimIndent()
        val lines = QrcLyricParser.parse(xml)
        assertEquals(1, lines.size)
        val line = lines[0]
        assertEquals("Rock & Roll's", line.text)
        assertEquals(0L, line.timeMs)
        assertEquals(4000L, line.durationMs)
        assertEquals(2, line.words.size)
        assertEquals("Rock & Roll", line.words[0].text)
        assertEquals("'", line.words[1].text.take(1))
    }

    @Test
    fun `parseLocal自动嗅探QRC格式`() {
        val qrc = "[0,2000]测试(0,1000)歌词(1000,1000)"
        val lines = LyricParser.parseLocal(qrc)
        assertEquals(1, lines.size)
        assertEquals(2, lines[0].words.size)
        assertEquals("测试歌词", lines[0].text)
    }

    @Test
    fun `parseLocal自动嗅探YRC格式`() {
        val yrc = "[0,2000](0,1000,0)网易(1000,1000,0)逐字"
        val lines = LyricParser.parseLocal(yrc)
        assertEquals(1, lines.size)
        assertEquals(2, lines[0].words.size)
        assertEquals("网易逐字", lines[0].text)
    }

    @Test
    fun `parseLocal自动嗅探TTML格式`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p begin="00:01.000" end="00:03.000"><span begin="00:01.000" end="00:02.000">Apple </span><span begin="00:02.000" end="00:03.000">Music</span></p>
                    </div>
                </body>
            </tt>
        """.trimIndent()
        val lines = LyricParser.parseLocal(ttml)
        assertEquals(1, lines.size)
        assertEquals(1000L, lines[0].timeMs)
        assertEquals(2000L, lines[0].durationMs)
        assertEquals("Apple Music", lines[0].text)
        assertEquals(2, lines[0].words.size)
    }

    @Test
    fun `本地歌词解析ESLyric方括号逐字标签`() {
        val lineStr = "[00:33.095]我[00:33.348]跟[00:33.652]你[00:34.864]本[00:35.271]应[00:35.576]该[00:36.082]"
        val line = LyricParser.parseLocal(lineStr).single()
        assertEquals(33095L, line.timeMs)
        assertEquals("我跟你本应该", line.text)
        assertEquals(listOf("我", "跟", "你", "本", "应", "该"), line.words.map { it.text })
        assertEquals(listOf(0L, 253L, 557L, 1769L, 2176L, 2481L), line.words.map { it.startOffsetMs })
        assertEquals(listOf(253L, 304L, 1212L, 407L, 305L, 506L), line.words.map { it.durationMs })
    }
}
