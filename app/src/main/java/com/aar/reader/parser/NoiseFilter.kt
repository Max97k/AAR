package com.aar.reader.parser

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Filter for non-content UI widgets, action controls, and decorative noise.
 */
object NoiseFilter {

    val BLACKLISTED_EXACT_LABELS = setOf(
        // Traditional / Simplified Chinese controls
        "複製", "分享", "返回", "更多選項", "更多", "讚", "留言", "關閉", "搜尋",
        "發送", "設定", "確定", "取消", "首頁", "選單", "上一頁", "下一頁", "重整",
        "复制", "分享", "返回", "更多选项", "更多", "赞", "评论", "关闭", "搜索",
        "发送", "设置", "确定", "取消", "首页", "菜单",

        // English controls
        "copy", "share", "back", "more options", "more", "like", "comment",
        "close", "search", "send", "settings", "ok", "cancel", "home", "menu",
        "previous", "next", "refresh", "navigate up"
    )

    data class NodeFilterAttributes(
        val isPassword: Boolean = false,
        val isVisibleToUser: Boolean = true,
        val packageName: String? = null,
        val bounds: Rect = Rect(),
        val text: CharSequence? = null,
        val contentDescription: CharSequence? = null,
        val isClickable: Boolean = false,
        val className: CharSequence? = null
    )

    fun shouldFilter(
        attributes: NodeFilterAttributes,
        screenBounds: Rect,
        overlayPackageName: String = "com.aar.reader"
    ): Boolean {
        // 1. Password input fields MUST be skipped for privacy
        if (attributes.isPassword) {
            return true
        }

        // 2. Invisible or non-interactive hidden nodes
        if (!attributes.isVisibleToUser) {
            return true
        }

        // 3. Exclude AAR's own overlay windows
        if (attributes.packageName == overlayPackageName ||
            attributes.packageName == "$overlayPackageName.debug"
        ) {
            return true
        }

        // 4. Check bounding box dimensions
        val nodeBounds = attributes.bounds
        val width = nodeBounds.right - nodeBounds.left
        val height = nodeBounds.bottom - nodeBounds.top
        if (width <= 0 || height <= 0) {
            return true
        }

        // Must intersect with visible screen area
        val intersects = nodeBounds.left < screenBounds.right &&
                         screenBounds.left < nodeBounds.right &&
                         nodeBounds.top < screenBounds.bottom &&
                         screenBounds.top < nodeBounds.bottom
        if (!intersects) {
            return true
        }

        // 5. Text extraction and noise blacklist check
        val text = attributes.text?.toString()?.trim()
        val desc = attributes.contentDescription?.toString()?.trim()
        val content = if (!text.isNullOrBlank()) text else desc

        // If there's no textual content, skip it
        if (content.isNullOrBlank()) {
            return true
        }

        // 6. If it's a clickable button or action widget with a blacklisted control label
        val lowerContent = content.lowercase()
        if (BLACKLISTED_EXACT_LABELS.contains(lowerContent)) {
            if (attributes.isClickable || attributes.className?.contains("Button", ignoreCase = true) == true) {
                return true
            }
        }

        // 7. Pure punctuation or symbols without alphanumeric/CJK characters
        if (content.all { it.isWhitespace() || !it.isLetterOrDigit() }) {
            return true
        }

        return false
    }

    /**
     * Checks if a node should be pruned from content extraction.
     */
    fun shouldFilterNode(
        node: AccessibilityNodeInfo,
        screenBounds: Rect,
        overlayPackageName: String = "com.aar.reader"
    ): Boolean {
        val rect = Rect().apply {
            node.getBoundsInScreen(this)
        }
        val attrs = NodeFilterAttributes(
            isPassword = node.isPassword,
            isVisibleToUser = node.isVisibleToUser,
            packageName = node.packageName?.toString(),
            bounds = rect,
            text = node.text,
            contentDescription = node.contentDescription,
            isClickable = node.isClickable,
            className = node.className
        )
        return shouldFilter(attrs, screenBounds, overlayPackageName)
    }
}
