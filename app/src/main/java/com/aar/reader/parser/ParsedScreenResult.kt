package com.aar.reader.parser

import android.view.accessibility.AccessibilityNodeInfo
import com.aar.reader.service.model.SentenceUnit

/**
 * Result of parsing an active screen view hierarchy.
 */
data class ParsedScreenResult(
    val sentences: List<SentenceUnit>,
    val scrollableContainer: AccessibilityNodeInfo?,
    val screenContentSignature: Long
)
