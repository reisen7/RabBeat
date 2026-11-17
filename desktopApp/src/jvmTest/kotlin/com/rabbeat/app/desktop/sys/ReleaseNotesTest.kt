package com.rabbeat.app.desktop.sys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseNotesTest {
    @Test
    fun headingsBulletsAndParagraphs() {
        val blocks = parseReleaseNotes(
            """
            ### 新增

            - Windows 桌面版
            - 导入 `.ncm`

            **完整对比**: https://github.com/reisen7/RabBeat/compare/v0.0.4...v0.0.5
            """.trimIndent(),
        )
        assertEquals(ReleaseBlock.Heading("新增"), blocks[0])
        assertEquals(ReleaseBlock.Bullet("Windows 桌面版"), blocks[1])
        assertTrue(blocks[2] is ReleaseBlock.Bullet)
        assertTrue(blocks[3] is ReleaseBlock.Paragraph)
    }

    @Test
    fun inlineBoldCodeAndLink() {
        val spans = parseInline("安装版：`RabBeat.exe`，**完整对比**: https://github.com/reisen7/RabBeat")
        assertEquals(MdSpan.Text("安装版："), spans[0])
        assertEquals(MdSpan.Text("RabBeat.exe", code = true), spans[1])
        assertEquals(MdSpan.Text("，"), spans[2])
        assertEquals(MdSpan.Text("完整对比", bold = true), spans[3])
        assertEquals(MdSpan.Text(": "), spans[4])
        assertEquals(
            MdSpan.Link("https://github.com/reisen7/RabBeat", "https://github.com/reisen7/RabBeat"),
            spans[5],
        )
    }
}
