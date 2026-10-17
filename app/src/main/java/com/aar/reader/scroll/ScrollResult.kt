package com.aar.reader.scroll

/**
 * Outcome of an auto-scroll attempt.
 */
sealed class ScrollResult {
    data object SuccessContainer : ScrollResult()
    data object SuccessGesture : ScrollResult()
    data object Failed : ScrollResult()
}
