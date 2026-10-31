package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

/** 根据行与容器的布局参数确定尺寸。 */
internal object HomeCardLayoutRules {
    private const val WRAP_CONTENT = -2

    fun rowHeight(rowHeight: Int, groupHeight: Int): Int = when {
        rowHeight > 0 -> rowHeight
        rowHeight == WRAP_CONTENT -> WRAP_CONTENT
        groupHeight > 0 -> groupHeight // 旧版 MATCH_PARENT 行沿用原横向容器高度。
        else -> WRAP_CONTENT
    }

    fun hiddenFeedStickyOffset(headHeight: Int, visibleHeight: Int, navHeight: Int): Int =
        (visibleHeight - navHeight).coerceIn(0, headHeight.coerceAtLeast(0))

}
