package com.xiyunmn.puredupan.hook.ui.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.xiyunmn.puredupan.hook.ui.UiStyle

/** 保留宿主遮罩，单独渐变父弹窗的压暗效果。 */
internal object SettingsDialogShade {
    private const val DURATION_MS = 360L
    private val shades = mutableMapOf<View, Shade>()

    fun bind(parent: Window, child: Window) {
        val targetAlpha = (child.attributes.dimAmount * 255).toInt().coerceIn(0, 255)
        child.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        child.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            private var shade: Shade? = null

            override fun onViewAttachedToWindow(view: View) {
                val root = parent.decorView
                shade = shades.getOrPut(root) { Shade(root) }.also {
                    it.coverWith(view, targetAlpha)
                }
            }

            override fun onViewDetachedFromWindow(view: View) {
                shade?.uncover(view)
                view.removeOnAttachStateChangeListener(this)
            }
        })
    }

    private class Shade(private val root: View) : View.OnAttachStateChangeListener,
        View.OnLayoutChangeListener {
        private val tokens = UiStyle.tokens(root.context)
        private var child: View? = null
        private var dimAlpha = 0
        private var requestedAlpha: Int? = null
        private val drawable = InsetDrawable(
            GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = tokens.cardCornerPx
            },
            tokens.dialogInsetPx,
        ).apply { alpha = 0 }
        private var animator: ValueAnimator? = null

        init {
            updateBounds()
            root.overlay.add(drawable)
            root.addOnAttachStateChangeListener(this)
            root.addOnLayoutChangeListener(this)
        }

        fun coverWith(view: View, targetAlpha: Int) {
            stopAnimation()
            child?.removeOnLayoutChangeListener(this)
            child = view
            dimAlpha = targetAlpha
            requestedAlpha = null
            view.addOnLayoutChangeListener(this)
            updateBounds()
        }

        fun uncover(view: View) {
            if (child !== view) return
            view.removeOnLayoutChangeListener(this)
            child = null
            fadeTo(0, removeWhenFinished = true)
        }

        private fun updateBounds() {
            drawable.setBounds(0, 0, root.width, root.height)
            child?.takeIf { it.width > 0 && it.height > 0 }?.let { view ->
                // 以布局后的实际宽高判断，同尺寸弹窗不额外压暗父页面。
                val alpha = if (view.width == root.width && view.height == root.height) 0 else dimAlpha
                if (requestedAlpha != alpha) {
                    requestedAlpha = alpha
                    fadeTo(alpha)
                }
            }
        }

        private fun fadeTo(alpha: Int, removeWhenFinished: Boolean = false) {
            stopAnimation()
            if (!root.isAttachedToWindow) {
                remove()
                return
            }
            if (drawable.alpha == alpha) {
                if (removeWhenFinished) remove()
                return
            }
            animator = ValueAnimator.ofInt(drawable.alpha, alpha).apply {
                duration = DURATION_MS
                addUpdateListener { drawable.alpha = it.animatedValue as Int }
                if (removeWhenFinished) {
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) = remove()
                    })
                }
                start()
            }
        }

        private fun stopAnimation() {
            animator?.removeAllListeners()
            animator?.cancel()
            animator = null
        }

        private fun remove() {
            stopAnimation()
            child?.removeOnLayoutChangeListener(this)
            child = null
            root.overlay.remove(drawable)
            root.removeOnAttachStateChangeListener(this)
            root.removeOnLayoutChangeListener(this)
            if (shades[root] === this) shades.remove(root)
        }

        override fun onViewAttachedToWindow(view: View) = Unit

        override fun onViewDetachedFromWindow(view: View) = remove()

        override fun onLayoutChange(
            view: View, left: Int, top: Int, right: Int, bottom: Int,
            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
        ) {
            updateBounds()
        }
    }
}
