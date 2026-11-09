package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.ui.HostThemeChangeDispatcher
import com.xiyunmn.puredupan.hook.ui.UiStyle
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 布局完成后、绘制前合并会员卡背景更新。 */
internal object MemberCardBackgroundLayout {
    // 映射值也使用弱引用，避免待执行观察者持有整棵视图树。
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

    /** 只处理独立背景控件的图片与背景 drawable，不影响同级文字。 */
    fun applyDimming(view: ImageView, snapshot: SettingsSnapshot) {
        val percent = if (!snapshot.isMemberCardCustomizeEnabled) {
            0
        } else if (UiStyle.isNight(view.context)) {
            snapshot.memberCardBackgroundDimDarkPercent
        } else {
            snapshot.memberCardBackgroundDimLightPercent
        }
        bindings[view]?.get()?.applyDimming(view, percent.coerceIn(0, 100))
    }

    private class Binding(view: ImageView) : View.OnLayoutChangeListener,
        View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        private val target = WeakReference(view)
        private var observer: ViewTreeObserver? = null
        private var unregisterTheme: (() -> Unit)? = null
        private var imageFilter: ColorFilter? = null
        private var appliedImageFilter: ColorFilter? = null
        private var backgroundDrawable: Drawable? = null
        private var backgroundFilter: ColorFilter? = null
        private var appliedBackgroundFilter: ColorFilter? = null
        var render: ((ImageView) -> Unit)? = null

        init {
            if (view.isAttachedToWindow) observeTheme()
        }

        fun applyDimming(view: ImageView, percent: Int) {
            val channel = ((100 - percent) * 255 + 50) / 100
            val filter = if (percent == 0) null else
                PorterDuffColorFilter(Color.rgb(channel, channel, channel), PorterDuff.Mode.MULTIPLY)
            if (filter != null) {
                if (appliedImageFilter == null || view.colorFilter !== appliedImageFilter) {
                    imageFilter = view.colorFilter
                }
                view.colorFilter = filter
            } else if (appliedImageFilter != null && view.colorFilter === appliedImageFilter) {
                view.colorFilter = imageFilter
            }
            appliedImageFilter = filter

            if (backgroundDrawable !== view.background) {
                restoreBackgroundFilter()
                backgroundDrawable = view.background?.mutate()
                backgroundFilter = backgroundDrawable?.colorFilter
            }
            backgroundDrawable?.let { drawable ->
                if (filter != null) {
                    if (appliedBackgroundFilter == null || drawable.colorFilter !== appliedBackgroundFilter) {
                        backgroundFilter = drawable.colorFilter
                    }
                    drawable.colorFilter = filter
                } else {
                    restoreBackgroundFilter()
                }
            }
            appliedBackgroundFilter = filter
        }

        private fun restoreBackgroundFilter() {
            backgroundDrawable?.let { drawable ->
                if (appliedBackgroundFilter != null && drawable.colorFilter === appliedBackgroundFilter) {
                    drawable.colorFilter = backgroundFilter
                }
            }
            appliedBackgroundFilter = null
        }

        private fun observeTheme() {
            if (unregisterTheme == null) {
                unregisterTheme = HostThemeChangeDispatcher.register { schedule() }
            }
        }

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

        override fun onViewAttachedToWindow(view: View) {
            observeTheme()
            schedule()
        }

        override fun onViewDetachedFromWindow(view: View) {
            unregisterTheme?.invoke()
            unregisterTheme = null
            cancel()
        }
    }
}
