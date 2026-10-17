package com.aar.reader.parser

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class SpatialSorterTest {

    private fun testRect(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }

    @Test
    fun sortBoundsAndData_sortsTopToBottom() {
        val item1 = testRect(50, 300, 200, 350) to "Bottom line"
        val item2 = testRect(50, 100, 200, 150) to "Top line"
        val item3 = testRect(50, 200, 200, 250) to "Middle line"

        val sorted = SpatialSorter.sortBoundsAndData(listOf(item1, item2, item3))

        assertEquals("Top line", sorted[0].second)
        assertEquals("Middle line", sorted[1].second)
        assertEquals("Bottom line", sorted[2].second)
    }

    @Test
    fun sortBoundsAndData_sortsLeftToRightWithinToleranceCluster() {
        // Line 1: two items on same visual row (tops at 100 and 105, within 18px tolerance)
        val rightCol = testRect(300, 105, 500, 150) to "Right Col"
        val leftCol = testRect(50, 100, 250, 150) to "Left Col"

        // Line 2: item at top 250
        val nextRow = testRect(50, 250, 500, 300) to "Next Row"

        val sorted = SpatialSorter.sortBoundsAndData(listOf(rightCol, nextRow, leftCol))

        assertEquals("Left Col", sorted[0].second)
        assertEquals("Right Col", sorted[1].second)
        assertEquals("Next Row", sorted[2].second)
    }
}
