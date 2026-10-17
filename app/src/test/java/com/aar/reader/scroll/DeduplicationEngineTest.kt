package com.aar.reader.scroll

import android.graphics.Rect
import com.aar.reader.service.model.SentenceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeduplicationEngineTest {

    private lateinit var engine: DeduplicationEngine

    @Before
    fun setUp() {
        engine = DeduplicationEngine(maxHistorySize = 10, maxTailAnchors = 2)
    }

    private fun createSentence(id: String, text: String, top: Int = 100): SentenceUnit {
        return SentenceUnit(
            id = id,
            text = text,
            boundsInScreen = Rect().apply {
                left = 50
                this.top = top
                right = 500
                bottom = top + 50
            },
            windowId = 1,
            packageName = "com.test.app"
        )
    }

    @Test
    fun filterUnread_returnsAllWhenNoHistory() {
        val s1 = createSentence("1", "第一句測試內容。")
        val s2 = createSentence("2", "第二句測試內容。")

        val unread = engine.filterUnread(listOf(s1, s2), 2000)
        assertEquals(2, unread.size)
    }

    @Test
    fun filterUnread_filtersOutRecordedSentences() {
        val s1 = createSentence("1", "這是一段已經朗讀過的文字。")
        val s2 = createSentence("2", "這是第二段文字。")
        val s3 = createSentence("3", "這是全新的第三段文字。")

        engine.recordRead(s1)
        engine.recordRead(s2)

        // After scrolling, s2 is still visible at the top, and s3 is new
        val newScreen = listOf(s2, s3)
        val unread = engine.filterUnread(newScreen, 2000)

        assertEquals("Only s3 should be unread", 1, unread.size)
        assertEquals("這是全新的第三段文字。", unread[0].text)
    }

    @Test
    fun filterUnread_anchorSequencePrunesTopOverlap() {
        val s1 = createSentence("1", "段落 A。")
        val s2 = createSentence("2", "段落 B。")
        val s3 = createSentence("3", "段落 C。")
        val s4 = createSentence("4", "段落 D。")

        engine.recordRead(s1)
        engine.recordRead(s2) // s2 is the latest tail anchor

        // After scroll, viewport contains s2, s3, s4
        val newScreen = listOf(s2, s3, s4)
        val unread = engine.filterUnread(newScreen, 2000)

        assertEquals(2, unread.size)
        assertEquals("段落 C。", unread[0].text)
        assertEquals("段落 D。", unread[1].text)
    }

    @Test
    fun filterUnread_anchorCutoffPrunesUnrecordedHeaderAboveAnchor() {
        val s1 = createSentence("1", "第一段已讀內容。", top = 200)
        val s2 = createSentence("2", "第二段錨點內容。", top = 400)
        engine.recordRead(s1)
        engine.recordRead(s2)

        // After scroll, a mutated sticky timestamp header appears above s2, followed by new s3
        val mutatedStickyHeader = createSentence("header", "更新於 2 分鐘前", top = 80)
        val anchorOnNewScreen = createSentence("2_new", "第二段錨點內容。", top = 300)
        val s3 = createSentence("3", "第三段全新內容。", top = 600)

        val unread = engine.filterUnread(listOf(mutatedStickyHeader, anchorOnNewScreen, s3), 2000)

        assertEquals("Lines above the matched tail anchor must be pruned as overlap", 1, unread.size)
        assertEquals("第三段全新內容。", unread[0].text)
    }

    @Test
    fun reset_clearsAllHistory() {
        val s1 = createSentence("1", "已讀句子。")
        engine.recordRead(s1)

        engine.reset()

        val unread = engine.filterUnread(listOf(s1), 2000)
        assertEquals("After reset, s1 should be treated as unread", 1, unread.size)
    }

    @Test
    fun ringBuffer_evictsOldestWhenCapacityExceeded() {
        // maxHistorySize = 10
        for (i in 1..10) {
            engine.recordRead(createSentence("id_$i", "Sentence number $i."))
        }

        // Sentence 1 should still be known
        val s1 = createSentence("id_1", "Sentence number 1.")
        assertTrue(engine.filterUnread(listOf(s1), 2000).isEmpty())

        // Add 11th sentence, evicting Sentence 1
        engine.recordRead(createSentence("id_11", "Sentence number 11."))

        // Now Sentence 1 should be evicted and considered unread
        val unread = engine.filterUnread(listOf(s1), 2000)
        assertEquals(1, unread.size)
    }
}
