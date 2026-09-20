package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Coalesces card updates after layout, before drawing, without blocking the host's frame. */
internal object MemberCardBackgroundLayout {
    // Values must also be weak: a pending observer can otherwise retain its whole view tree.
    private val bindings = WeakHashMap<ImageView, WeakReference<Binding>>()

    fun apply(view: ImageView, render: (ImageView) -> Unit) {
        val binding = bindings[view]?.get() ?: Binding(view).also {
            bindings[view] = WeakReference(it)
            view.addOnLayoutChangeListener(it)
            view.addOnAttachStateChangeListener(it)
        }
        binding.render = render
        binding.schedule()
    }

    private class Binding(view: ImageView) : View.OnLayoutChangeListener,
        View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        private val target = WeakReference(view)
        private var observer: ViewTreeObserver? = null
        var render: ((ImageView) -> Unit)? = null

        fun schedule() {
            val view = target.get() ?: return
            if (!view.isAttachedToWindow || observer != null) return
            view.viewTreeObserver.takeIf { it.isAlive }?.let {
                observer = it
                it.addOnPreDrawListener(this)
                view.invalidate()
            }
        }

        private fun cancel() {
            observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
            observer = null
        }

        override fun onPreDraw(): Boolean {
            cancel()
            target.get()?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 }
                ?.let {
                    try {
                        render?.invoke(it)
                    } catch (e: Exception) {
                        XposedCompat.logW("[MemberCardBackgroundLayout] render failed: ${e.message}")
                    }
                }
            return true
        }

        override fun onLayoutChange(
            view: View, left: Int, top: Int, right: Int, bottom: Int,
            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
        ) {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) schedule()
        }

        override fun onViewAttachedToWindow(view: View) = schedule()

        override fun onViewDetachedFromWindow(view: View) = cancel()
    }
}
