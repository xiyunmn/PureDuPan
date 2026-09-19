package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

/** LayoutParams 契约；不依赖宿主版本、密度或固定行高。 */
internal object HomeCardLayoutRules {
    private const val WRAP_CONTENT = -2

    fun rowHeight(rowHeight: Int, groupHeight: Int): Int = when {
        rowHeight > 0 -> rowHeight
        rowHeight == WRAP_CONTENT -> WRAP_CONTENT
        groupHeight > 0 -> groupHeight // 旧版 MATCH_PARENT 行继承原横向容器高度。
        else -> WRAP_CONTENT
    }

    fun hiddenFeedStickyOffset(headHeight: Int, visibleHeight: Int, navHeight: Int): Int =
        (visibleHeight - navHeight).coerceIn(0, headHeight.coerceAtLeast(0))

}
