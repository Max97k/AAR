package com.aar.reader.parser

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * Top-to-bottom, left-to-right topological coordinate ordering using Y-axis tolerance clustering.
 */
interface ISpatialSorter {
    fun sortNodes(nodes: List<AccessibilityNodeInfo>, yTolerancePx: Int = 18): List<AccessibilityNodeInfo>
    fun <T> sortBoundsAndData(items: List<Pair<Rect, T>>, yTolerancePx: Int = 18): List<Pair<Rect, T>>
}

object SpatialSorter : ISpatialSorter {

    override fun sortNodes(
        nodes: List<AccessibilityNodeInfo>,
        yTolerancePx: Int
    ): List<AccessibilityNodeInfo> {
        if (nodes.size <= 1) return nodes

        val boundsList = nodes.map { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            node to rect
        }

        return sortBoundsAndData(boundsList.map { it.second to it.first }, yTolerancePx).map { it.second }
    }

    override fun <T> sortBoundsAndData(
        items: List<Pair<Rect, T>>,
        yTolerancePx: Int
    ): List<Pair<Rect, T>> {
        if (items.size <= 1) return items

        // Sort primarily by top coordinate first
        val sortedByTop = items.sortedWith(compareBy({ it.first.top }, { it.first.left }))

        // Cluster items whose top is within yTolerancePx
        val clustered = mutableListOf<MutableList<Pair<Rect, T>>>()
        for (item in sortedByTop) {
            val lastCluster = clustered.lastOrNull()
            if (lastCluster != null) {
                val clusterTop = lastCluster.first().first.top
                if (abs(item.first.top - clusterTop) <= yTolerancePx) {
                    lastCluster.add(item)
                    continue
                }
            }
            clustered.add(mutableListOf(item))
        }

        // Within each cluster, sort by left coordinate (left-to-right)
        val result = mutableListOf<Pair<Rect, T>>()
        for (cluster in clustered) {
            cluster.sortBy { it.first.left }
            result.addAll(cluster)
        }

        return result
    }
}
