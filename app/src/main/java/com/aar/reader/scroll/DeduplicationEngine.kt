package com.aar.reader.scroll

import com.aar.reader.service.model.SentenceUnit
import java.util.ArrayDeque

interface IDeduplicationEngine {
    fun filterUnread(sentences: List<SentenceUnit>, screenHeight: Int): List<SentenceUnit>
    fun recordRead(sentence: SentenceUnit)
    fun reset()
}

/**
 * Sliding-window text fingerprint ring buffer (K = 50) and tail-anchor matching (M = 3)
 * to eliminate overlapping content remaining in the upper viewport after partial scrolls.
 */
class DeduplicationEngine(
    private val maxHistorySize: Int = 50,
    private val maxTailAnchors: Int = 3
) : IDeduplicationEngine {

    // Ring buffer storing normalized 64-bit FNV hashes of recently read sentences
    private val readFingerprints = ArrayDeque<Long>(maxHistorySize)
    private val readFingerprintSet = HashSet<Long>()

    // Last M read sentences for anchor sequence matching
    private val tailAnchors = ArrayDeque<String>(maxTailAnchors)

    @Synchronized
    override fun recordRead(sentence: SentenceUnit) {
        val normalized = normalizeText(sentence.text)
        if (normalized.isEmpty()) return

        val hash = computeNormalizedHash(sentence.text)
        if (readFingerprints.size >= maxHistorySize) {
            val removed = readFingerprints.removeFirst()
            if (!readFingerprints.contains(removed)) {
                readFingerprintSet.remove(removed)
            }
        }
        readFingerprints.addLast(hash)
        readFingerprintSet.add(hash)

        if (tailAnchors.size >= maxTailAnchors) {
            tailAnchors.removeFirst()
        }
        tailAnchors.addLast(normalized)
    }

    @Synchronized
    override fun filterUnread(
        sentences: List<SentenceUnit>,
        screenHeight: Int
    ): List<SentenceUnit> {
        if (sentences.isEmpty()) return emptyList()
        if (readFingerprintSet.isEmpty()) return sentences

        // Pass 1: Tail-Anchor Viewport Cutoff
        // Find the latest sentence index in the upper/middle viewport that matches our most recent tail anchors.
        // Anything at or above the matched tail anchor is viewport overlap from before the scroll.
        var anchorCutoffIndex = -1
        val anchorsNewestFirst = tailAnchors.toList().asReversed()
        for (anchor in anchorsNewestFirst) {
            if (anchor.isEmpty()) continue
            val matchedIndex = sentences.indexOfLast { sentence ->
                normalizeText(sentence.text) == anchor &&
                    (screenHeight <= 0 || sentence.boundsInScreen.top <= (screenHeight * 0.85f).toInt())
            }
            if (matchedIndex >= 0) {
                anchorCutoffIndex = matchedIndex
                break
            }
        }

        // Pass 2: Sliding-Window Fingerprint Filtering after the anchor cutoff
        val unreadList = mutableListOf<SentenceUnit>()
        val startIndex = anchorCutoffIndex + 1

        for (i in startIndex until sentences.size) {
            val sentence = sentences[i]
            val normalized = normalizeText(sentence.text)
            if (normalized.isEmpty()) continue

            val hash = computeNormalizedHash(sentence.text)
            if (readFingerprintSet.contains(hash)) {
                // Already read sentence in viewport overlap
                continue
            }

            unreadList.add(sentence)
        }

        return unreadList
    }

    @Synchronized
    override fun reset() {
        readFingerprints.clear()
        readFingerprintSet.clear()
        tailAnchors.clear()
    }

    companion object {
        fun normalizeText(text: String): String {
            return text.filter { it.isLetterOrDigit() }.lowercase()
        }

        fun computeNormalizedHash(text: String): Long {
            val normalized = normalizeText(text)
            var hash = -3750763034362895579L
            val prime = 1099511628211L
            for (c in normalized) {
                hash = hash xor c.code.toLong()
                hash = hash * prime
            }
            return hash
        }
    }
}
