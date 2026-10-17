package com.aar.reader.parser

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.aar.reader.service.model.SentenceUnit

interface IScreenTreeParser {
    fun parseScreen(
        root: AccessibilityNodeInfo,
        screenBounds: Rect,
        overlayPackageName: String = "com.aar.reader"
    ): ParsedScreenResult
}

object ScreenTreeParser : IScreenTreeParser {

    override fun parseScreen(
        root: AccessibilityNodeInfo,
        screenBounds: Rect,
        overlayPackageName: String
    ): ParsedScreenResult {
        val validNodes = mutableListOf<NodeContent>()
        var bestScrollableContainer: AccessibilityNodeInfo? = null
        var maxContainerArea = -1

        // 1. Recursive DFS traversal
        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val nodePkg = node.packageName?.toString()
            val isExcludedPackage = nodePkg == overlayPackageName ||
                nodePkg == "$overlayPackageName.debug" ||
                nodePkg == "com.android.systemui"

            // Check if this node is a visible candidate scrollable container
            if (node.isScrollable && node.isVisibleToUser && !isExcludedPackage) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val area = (bounds.right - bounds.left) * (bounds.bottom - bounds.top)
                val supportsScroll = node.actionList.any {
                    it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ||
                    it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id
                }
                if (supportsScroll && area > maxContainerArea) {
                    maxContainerArea = area
                    bestScrollableContainer = node
                }
            }

            // Check if node is valid content
            if (!NoiseFilter.shouldFilterNode(node, screenBounds, overlayPackageName)) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val text = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()
                val content = if (!text.isNullOrBlank()) text else desc

                if (!content.isNullOrBlank()) {
                    validNodes.add(
                        NodeContent(
                            text = content,
                            bounds = bounds,
                            windowId = node.windowId,
                            packageName = nodePkg ?: ""
                        )
                    )
                }
            }

            // Traverse children
            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = node.getChild(i)
                dfs(child)
            }
        }

        dfs(root)

        // 2. Spatial coordinate sorting (top-to-bottom, left-to-right)
        val sortedNodes = SpatialSorter.sortBoundsAndData(
            validNodes.map { it.bounds to it }
        ).map { it.second }

        // 3. Sentence segmentation
        val allSentences = mutableListOf<SentenceUnit>()
        for (node in sortedNodes) {
            val sentences = SentenceSplitter.splitIntoSentences(
                text = node.text,
                bounds = node.bounds,
                windowId = node.windowId,
                packageName = node.packageName
            )
            allSentences.addAll(sentences)
        }

        // 4. Compute screen content signature (FNV-1a 64-bit hash)
        val signature = computeContentSignature(allSentences)

        return ParsedScreenResult(
            sentences = allSentences,
            scrollableContainer = bestScrollableContainer,
            screenContentSignature = signature
        )
    }

    private fun computeContentSignature(sentences: List<SentenceUnit>): Long {
        var hash = -3750763034362895579L // FNV offset basis
        val prime = 1099511628211L       // FNV prime

        for (s in sentences) {
            for (c in s.text) {
                hash = hash xor c.code.toLong()
                hash = hash * prime
            }
            hash = hash xor s.boundsInScreen.top.toLong()
            hash = hash * prime
            hash = hash xor s.boundsInScreen.bottom.toLong()
            hash = hash * prime
        }

        return hash
    }

    private data class NodeContent(
        val text: String,
        val bounds: Rect,
        val windowId: Int,
        val packageName: String
    )
}
