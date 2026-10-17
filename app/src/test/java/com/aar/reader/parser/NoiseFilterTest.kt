package com.aar.reader.parser

import android.graphics.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseFilterTest {

    private val screenBounds = Rect().apply {
        left = 0
        top = 0
        right = 1080
        bottom = 2400
    }

    private fun testRect(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }

    @Test
    fun shouldFilter_filtersPasswordNodes() {
        val attrs = NoiseFilter.NodeFilterAttributes(
            isPassword = true,
            isVisibleToUser = true,
            packageName = "com.bank.app",
            bounds = testRect(10, 100, 500, 200),
            text = "secret123"
        )
        assertTrue("Password nodes must be filtered", NoiseFilter.shouldFilter(attrs, screenBounds))
    }

    @Test
    fun shouldFilter_filtersInvisibleNodes() {
        val attrs = NoiseFilter.NodeFilterAttributes(
            isPassword = false,
            isVisibleToUser = false,
            packageName = "com.news.app",
            bounds = testRect(10, 100, 500, 200),
            text = "Hidden Article Paragraph"
        )
        assertTrue("Invisible nodes must be filtered", NoiseFilter.shouldFilter(attrs, screenBounds))
    }

    @Test
    fun shouldFilter_filtersAarOverlayWindows() {
        val attrs = NoiseFilter.NodeFilterAttributes(
            isPassword = false,
            isVisibleToUser = true,
            packageName = "com.aar.reader",
            bounds = testRect(10, 100, 500, 200),
            text = "Pause Button"
        )
        assertTrue("AAR overlay nodes must be filtered to prevent self-reading loops",
            NoiseFilter.shouldFilter(attrs, screenBounds, overlayPackageName = "com.aar.reader"))
    }

    @Test
    fun shouldFilter_filtersZeroDimensions() {
        val attrs = NoiseFilter.NodeFilterAttributes(
            isPassword = false,
            isVisibleToUser = true,
            packageName = "com.news.app",
            bounds = testRect(10, 100, 10, 100), // width 0, height 0
            text = "Zero size text"
        )
        assertTrue("Zero dimensions must be filtered", NoiseFilter.shouldFilter(attrs, screenBounds))
    }

    @Test
    fun shouldFilter_filtersBlacklistedActionButtons() {
        val copyButton = NoiseFilter.NodeFilterAttributes(
            isPassword = false,
            isVisibleToUser = true,
            packageName = "com.news.app",
            bounds = testRect(10, 100, 100, 150),
            text = "複製",
            isClickable = true
        )
        assertTrue("Copy button must be filtered", NoiseFilter.shouldFilter(copyButton, screenBounds))

        val shareButton = NoiseFilter.NodeFilterAttributes(
            isPassword = false,
            isVisibleToUser = true,
            packageName = "com.news.app",
            bounds = testRect(10, 100, 100, 150),
            text = "Share",
            isClickable = true
        )
        assertTrue("Share button must be filtered", NoiseFilter.shouldFilter(shareButton, screenBounds))
    }

    @Test
    fun shouldFilter_filtersPurePunctuationAndWhitespace() {
        val punctAttrs = NoiseFilter.NodeFilterAttributes(
            isPassword = false,
            isVisibleToUser = true,
            packageName = "com.news.app",
            bounds = testRect(10, 100, 200, 150),
            text = "... --- !!!"
        )
        assertTrue("Pure punctuation must be filtered", NoiseFilter.shouldFilter(punctAttrs, screenBounds))
    }

    @Test
    fun shouldFilter_acceptsLegitimateArticleContent() {
        val articleAttrs = NoiseFilter.NodeFilterAttributes(
            isPassword = false,
            isVisibleToUser = true,
            packageName = "com.news.app",
            bounds = testRect(20, 300, 1000, 500),
            text = "這是一篇關於科技發展的新聞報導，讀者可以輕鬆理解內容。",
            isClickable = false
        )
        assertFalse("Legitimate article content must NOT be filtered",
            NoiseFilter.shouldFilter(articleAttrs, screenBounds))
    }
}
