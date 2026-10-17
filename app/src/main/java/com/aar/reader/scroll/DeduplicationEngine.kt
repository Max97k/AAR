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
        val hash = computeNormalizedHash(sentence.text)
        if (readFingerprints.size >= maxHistorySize) {
            val removed = readFingerprints.removeFirst()
            readFingerprintSet.remove(removed)
        }
        readFingerprints.addLast(hash)
        readFingerprintSet.add(hash)

        if (tailAnchors.size >= maxTailAnchors) {
            tailAnchors.removeFirst()
        }
        tailAnchors.addLast(normalizeText(sentence.text))
    }

    @Synchronized
    override fun filterUnread(
        sentences: List<SentenceUnit>,
        screenHeight: Int
    ): List<SentenceUnit> {
        if (sentences.isEmpty()) return emptyList()
        if (readFingerprintSet.isEmpty()) return sentences

        val unreadList = mutableListOf<SentenceUnit>()
        var foundAnchorCutoff = false

        // Check if any sentence near top matches the last tail anchor
        val lastAnchor = tailAnchors.lastOrNull()

        for (sentence in sentences) {
            val normalized = normalizeText(sentence.text)
            val hash = computeNormalizedHash(sentence.text)

            // If we match the tail anchor, anything above or at it is considered overlap
            if (lastAnchor != null && normalized == lastAnchor) {
                foundAnchorCutoff = true
                continue
            }

            // Check against sliding window fingerprint set
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
