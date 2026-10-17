package com.aar.reader.parser

import android.graphics.Rect
import com.aar.reader.service.model.SentenceUnit
import java.util.UUID

interface ISentenceSplitter {
    fun splitIntoSentences(
        text: String,
        bounds: Rect,
        windowId: Int,
        packageName: String,
        maxChunkLength: Int = 150
    ): List<SentenceUnit>
}

object SentenceSplitter : ISentenceSplitter {

    // Major sentence terminators (CJK and Western)
    private val SENTENCE_ENDINGS = setOf(
        '。', '！', '？', '；',
        '.', '!', '?', ';', '\n'
    )

    // Secondary delimiters for splitting overly long sentences (e.g. > maxChunkLength)
    private val SECONDARY_SPLITTERS = setOf(
        '，', '、', '：',
        ',', ':', '-', '—'
    )

    override fun splitIntoSentences(
        text: String,
        bounds: Rect,
        windowId: Int,
        packageName: String,
        maxChunkLength: Int
    ): List<SentenceUnit> {
        val raw = text.trim()
        if (raw.isEmpty()) return emptyList()

        val rawSentences = splitByTerminators(raw)
        val finalChunks = mutableListOf<String>()

        for (s in rawSentences) {
            if (s.length <= maxChunkLength) {
                finalChunks.add(s)
            } else {
                // Overlong sentence, sub-split by secondary delimiters or space
                val subChunks = subSplitLongSentence(s, maxChunkLength)
                finalChunks.addAll(subChunks)
            }
        }

        return finalChunks.mapIndexed { index, sentenceText ->
            SentenceUnit(
                id = "sent_${windowId}_${System.currentTimeMillis()}_${index}_${UUID.randomUUID().toString().take(6)}",
                text = sentenceText,
                boundsInScreen = Rect().apply {
                    left = bounds.left
                    top = bounds.top
                    right = bounds.right
                    bottom = bounds.bottom
                },
                windowId = windowId,
                packageName = packageName
            )
        }
    }

    private fun splitByTerminators(text: String): List<String> {
        val list = mutableListOf<String>()
        val current = StringBuilder()

        for (i in text.indices) {
            val c = text[i]
            current.append(c)
            if (SENTENCE_ENDINGS.contains(c)) {
                // Check if next char is also a punctuation mark like "！？" or quotation mark
                val nextIsClosing = i + 1 < text.length && (
                    text[i + 1] == '」' || text[i + 1] == '』' || text[i + 1] == '”' ||
                    text[i + 1] == '"' || text[i + 1] == '\'' || text[i + 1] == ')' || text[i + 1] == '）'
                )
                if (!nextIsClosing) {
                    val s = current.toString().trim()
                    if (s.isNotEmpty()) {
                        list.add(s)
                    }
                    current.clear()
                }
            }
        }

        if (current.isNotEmpty()) {
            val s = current.toString().trim()
            if (s.isNotEmpty()) {
                list.add(s)
            }
        }

        return list
    }

    private fun subSplitLongSentence(sentence: String, maxChunkLength: Int): List<String> {
        val list = mutableListOf<String>()
        var remaining = sentence

        while (remaining.length > maxChunkLength) {
            // Find best split point near maxChunkLength
            var splitIndex = -1
            for (i in maxChunkLength downTo (maxChunkLength / 2)) {
                if (i < remaining.length && (SECONDARY_SPLITTERS.contains(remaining[i]) || remaining[i].isWhitespace())) {
                    splitIndex = i + 1
                    break
                }
            }

            if (splitIndex == -1) {
                // No natural delimiter, split hard at maxChunkLength
                splitIndex = maxChunkLength
            }

            val chunk = remaining.substring(0, splitIndex).trim()
            if (chunk.isNotEmpty()) {
                list.add(chunk)
            }
            remaining = remaining.substring(splitIndex).trim()
        }

        if (remaining.isNotEmpty()) {
            list.add(remaining)
        }

        return list
    }
}
