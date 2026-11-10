package com.xiyunmn.puredupan.hook.ui.settings

internal object MemberCardBackgroundSelectionState {
    @Volatile private var listener: ((String) -> Unit)? = null
    @Volatile var revision: Long = 0
        private set

    fun setListener(newListener: (String) -> Unit) {
        listener = newListener
    }

    fun clearIfSame(expectedListener: (String) -> Unit) {
        if (listener === expectedListener) {
            listener = null
        }
    }

    fun notifySelected(uriString: String) {
        // 重新选择同一图片也会刷新授权，允许背景加载重试。
        revision++
        listener?.invoke(uriString)
    }
}
