package com.aar.reader.parser

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

    private fun testRect(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }

    @Test
    fun splitIntoSentences_splitsChinesePunctuationCorrectly() {
        val text = "這是一篇測試文章。包含了第一句話！還有第二個疑問句嗎？最後是分號；結束了。"
        val bounds = testRect(0, 0, 500, 100)
        val sentences = SentenceSplitter.splitIntoSentences(text, bounds, 1, "com.example.app")

        assertEquals(5, sentences.size)
        assertEquals("這是一篇測試文章。", sentences[0].text)
        assertEquals("包含了第一句話！", sentences[1].text)
        assertEquals("還有第二個疑問句嗎？", sentences[2].text)
        assertEquals("最後是分號；", sentences[3].text)
        assertEquals("結束了。", sentences[4].text)
        assertEquals(bounds.left, sentences[0].boundsInScreen.left)
        assertEquals(bounds.top, sentences[0].boundsInScreen.top)
        assertEquals(bounds.right, sentences[0].boundsInScreen.right)
        assertEquals(bounds.bottom, sentences[0].boundsInScreen.bottom)
    }

    @Test
    fun splitIntoSentences_splitsEnglishPunctuationCorrectly() {
        val text = "Welcome to AAR reader! This is a test sentence. Can it split properly? Yes, it definitely can."
        val bounds = testRect(10, 20, 300, 400)
        val sentences = SentenceSplitter.splitIntoSentences(text, bounds, 2, "com.example.app")

        assertEquals(4, sentences.size)
        assertEquals("Welcome to AAR reader!", sentences[0].text)
        assertEquals("This is a test sentence.", sentences[1].text)
        assertEquals("Can it split properly?", sentences[2].text)
        assertEquals("Yes, it definitely can.", sentences[3].text)
        assertEquals(bounds.left, sentences[0].boundsInScreen.left)
        assertEquals(bounds.top, sentences[0].boundsInScreen.top)
    }

    @Test
    fun splitIntoSentences_preservesDecimalNumbersAndCompoundTerminators() {
        val text = "目前版本為 v1.0.0，圓周率約為 3.14！真的嗎？！是的..."
        val bounds = testRect(0, 0, 500, 100)
        val sentences = SentenceSplitter.splitIntoSentences(text, bounds, 1, "com.example.app")

        assertEquals(3, sentences.size)
        assertEquals("目前版本為 v1.0.0，圓周率約為 3.14！", sentences[0].text)
        assertEquals("真的嗎？！", sentences[1].text)
        assertEquals("是的...", sentences[2].text)
    }

    @Test
    fun splitIntoSentences_subSplitsOverlongSentences() {
        val longText = "這是一段非常非常長的中文字串，中間有逗號區隔，但是整段文字長度超過了一百五十個字元限制，" +
            "我們希望自動斷句引擎能夠在逗號或者空白處進行次級切分，確保送到語音合成引擎的單次字串不會超標，" +
            "同時讓高亮框能夠更精準地鎖定當前朗讀的段落，這樣使用者的閱讀體驗才會流暢且自然。"
        val bounds = testRect(0, 0, 1000, 200)

        val sentences = SentenceSplitter.splitIntoSentences(
            longText,
            bounds,
            3,
            "com.example.app",
            maxChunkLength = 60
        )

        assertTrue("Should split into multiple sub-chunks", sentences.size >= 3)
        for (sentence in sentences) {
            assertTrue("Each sub-chunk should be within or near limit", sentence.text.length <= 65)
        }
    }
}
