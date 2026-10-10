package com.wyrix.news.data

import com.wyrix.news.data.remote.OgImageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OgImageParserTest {
    private val base = "https://news.example.com/world/story-1"

    @Test
    fun readsOpenGraphImage() {
        val html = """<html><head><meta property="og:image" content="https://cdn.example.com/a.jpg"></head>"""
        assertEquals("https://cdn.example.com/a.jpg", OgImageParser.parse(html, base))
    }

    @Test
    fun handlesReversedAttributeOrderAndSingleQuotes() {
        val html = "<meta content='https://cdn.example.com/b.jpg' property='og:image' />"
        assertEquals("https://cdn.example.com/b.jpg", OgImageParser.parse(html, base))
    }

    @Test
    fun resolvesRelativeUrlsAndUpgradesHttp() {
        assertEquals(
            "https://news.example.com/img/c.jpg",
            OgImageParser.parse("""<meta property="og:image" content="/img/c.jpg">""", base),
        )
        assertEquals(
            "https://cdn.example.com/d.jpg",
            OgImageParser.parse("""<meta property="og:image" content="http://cdn.example.com/d.jpg">""", base),
        )
    }

    @Test
    fun fallsBackToTwitterCardAndDecodesAmpersands() {
        val html = """<meta name="twitter:image" content="https://cdn.example.com/e.jpg?a=1&amp;b=2">"""
        assertEquals("https://cdn.example.com/e.jpg?a=1&b=2", OgImageParser.parse(html, base))
    }

    @Test
    fun prefersOpenGraphOverTwitter() {
        val html = """
            <meta name="twitter:image" content="https://cdn.example.com/t.jpg">
            <meta property="og:image" content="https://cdn.example.com/o.jpg">
        """.trimIndent()
        assertEquals("https://cdn.example.com/o.jpg", OgImageParser.parse(html, base))
    }

    @Test
    fun returnsNullWhenMissingOrUnusable() {
        assertNull(OgImageParser.parse("<html><head><title>x</title></head></html>", base))
        assertNull(OgImageParser.parse("""<meta property="og:image" content="data:image/png;base64,AAAA">""", base))
    }
}
