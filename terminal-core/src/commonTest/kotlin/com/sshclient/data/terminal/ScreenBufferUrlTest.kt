package com.sshclient.data.terminal

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenBufferUrlTest {
    private lateinit var buffer: ScreenBuffer

    @BeforeTest
    fun setup() {
        buffer = ScreenBuffer(24, 80)
    }

    @Test
    fun `getUrlRanges detects simple url`() {
        val url = "https://example.com"
        writeString(url)

        val row = buffer.getRow(0)
        val matches = ScreenBuffer.getUrlRanges(row)

        assertEquals(1, matches.size)
        assertEquals(url, ScreenBuffer.extractUrl(row, matches[0]))
        assertEquals(0, matches[0].startCol)
        assertEquals(url.length, matches[0].endCol)
    }

    @Test
    fun `getUrlRanges detects multiple urls on same line`() {
        val text = "Check https://a.com and http://b.org/foo"
        writeString(text)

        val row = buffer.getRow(0)
        val matches = ScreenBuffer.getUrlRanges(row)

        assertEquals(2, matches.size)

        assertEquals("https://a.com", ScreenBuffer.extractUrl(row, matches[0]))
        assertEquals("http://b.org/foo", ScreenBuffer.extractUrl(row, matches[1]))
    }

    private fun writeString(s: String) {
        for (c in s) {
            buffer.writeChar(c)
        }
    }
}
