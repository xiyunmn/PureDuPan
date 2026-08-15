package com.xiyunmn.puredupan.hook.ui.settings

internal object StorageTreeSelectionState {
    @Volatile private var listener: (() -> Unit)? = null

    fun setListener(newListener: () -> Unit) {
        listener = newListener
    }

    fun clearIfSame(expectedListener: () -> Unit) {
        if (listener === expectedListener) listener = null
    }

    fun notifySelected() {
        listener?.invoke()
    }
}
