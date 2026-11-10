package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.xiyunmn.puredupan.hook.runtime.ModuleLifetime
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduThemeHookPoints
import java.lang.reflect.Method

/** 在 Flutter 搜索路由重新加载皮肤时保持原窗口内容可见。 */
internal object FlutterSearchThemeRefresh {
    private val handler = Handler(Looper.getMainLooper())
    private val installed = mutableSetOf<Method>()
    private val transitions = mutableMapOf<Activity, Transition>()

    private class Transition(val decor: ViewGroup, val cover: ImageView, val completed: (Boolean) -> Unit) {
        var fragment: Any? = null
        var cleanup: Runnable? = null
        var rebuilt = false
        var pixelPending = false
        var frame: Runnable? = null
    }

    fun hook(cl: ClassLoader): Boolean {
        val mod = XposedCompat.module ?: return false
        val fragmentClass = XposedCompat.findClassOrNull(BaiduThemeHookPoints.FLUTTER_FRAGMENT, cl) ?: return false
        val displayed = fragmentClass.getMethod("onFlutterUiDisplayed")
        if (displayed in installed) return true
        mod.hook(displayed).setId("FlutterSearchThemeRefresh:uiDisplayed:${displayed.declaringClass.name}").intercept { chain ->
            if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return@intercept chain.proceed()
            val result = chain.proceed()
            transitions.entries.firstOrNull { it.value.fragment === chain.thisObject }?.let { (activity, state) ->
                // 等待新纹理提交到窗口后再移除遮罩。
                state.frame?.let(state.decor::removeCallbacks)
                state.frame = Runnable {
                    state.frame = Runnable { finish(activity, state) }.also(state.decor::postOnAnimation)
                }.also(state.decor::postOnAnimation)
            }
            result
        }
        installed += displayed
        return true
    }

    fun disposeForReload() {
        transitions.toMap().forEach { (activity, state) -> finish(activity, state) }
        handler.removeCallbacksAndMessages(null)
    }

    fun refresh(activity: Activity, isCurrent: () -> Boolean, completed: (Boolean) -> Unit) {
        if (ModuleLifetime.isRetiring) return
        if (transitions.containsKey(activity)) {
            completed(false)
            return
        }
        runCatching {
            val manager = activity.javaClass.getMethod("getSupportFragmentManager").invoke(activity)
            val saved = manager.javaClass.getMethod("isStateSaved")
            if (saved.invoke(manager) == true) {
                completed(false)
                return
            }
            val initFragment = activity.javaClass.getMethod("initFragment")
            val fragmentField = activity.javaClass.superclass!!.getDeclaredField("mFragment").apply { isAccessible = true }
            val execute = manager.javaClass.getMethod("executePendingTransactions")
            val decor = activity.window.decorView as ViewGroup
            val content = activity.findViewById<View>(android.R.id.content)
            if (content.width <= 0 || content.height <= 0) {
                completed(false)
                return
            }
            val location = IntArray(2).also(content::getLocationInWindow)
            val rect = Rect(location[0], location[1], location[0] + content.width, location[1] + content.height)
            val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
            val cover = ImageView(activity).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val state = Transition(decor, cover, completed)
            if (!ModuleLifetime.beginTask()) return
            transitions[activity] = state
            state.cleanup = Runnable { finish(activity, state) }.also { handler.postDelayed(it, 3000L) }
            if (!ModuleLifetime.beginTask()) {
                finish(activity, state)
                return
            }
            state.pixelPending = true
            try {
                PixelCopy.request(activity.window, rect, bitmap, { result ->
                    try {
                        if (transitions[activity] !== state) {
                            return@request
                        }
                        if (ModuleLifetime.isRetiring || result != PixelCopy.SUCCESS || activity.isDestroyed || activity.isFinishing ||
                            !isCurrent() || runCatching { saved.invoke(manager) != false }.getOrDefault(true)
                        ) {
                            finish(activity, state)
                            return@request
                        }
                        runCatching {
                            cover.setImageBitmap(bitmap)
                            decor.addView(cover, ViewGroup.LayoutParams(rect.width(), rect.height()))
                            val decorLocation = IntArray(2).also(decor::getLocationInWindow)
                            cover.x = (rect.left - decorLocation[0]).toFloat()
                            cover.y = (rect.top - decorLocation[1]).toFloat()
                            // 为透明 Activity 保留不透明背景，避免 Flutter 空帧露出底层主页。
                            activity.window.setBackgroundDrawable(ColorDrawable(bitmap.getPixel(bitmap.width / 2, 0) or (0xff shl 24)))
                            val fragment = initFragment.invoke(activity) ?: error("search fragment unavailable")
                            state.fragment = fragment
                            fragmentField.set(activity, fragment)
                            execute.invoke(manager)
                            state.rebuilt = true
                        }.onFailure {
                            finish(activity, state)
                            XposedCompat.logW("[FlutterSearchThemeRefresh] refresh failed: ${it.message}")
                        }
                    } finally {
                        state.pixelPending = false
                        ModuleLifetime.finishTask()
                    }
                }, handler)
            } catch (error: Throwable) {
                if (state.pixelPending) {
                    state.pixelPending = false
                    ModuleLifetime.finishTask()
                }
                throw error
            }
        }.onFailure {
            if (transitions.containsKey(activity)) cancel(activity) else completed(false)
            XposedCompat.logW("[FlutterSearchThemeRefresh] prepare failed: ${it.message}")
        }
    }

    fun cancel(activity: Activity) {
        transitions[activity]?.let { finish(activity, it) }
    }

    private fun finish(activity: Activity, state: Transition) {
        if (transitions[activity] !== state) return
        transitions.remove(activity)
        state.cleanup?.let(handler::removeCallbacks)
        state.frame?.let(state.decor::removeCallbacks)
        (state.cover.parent as? ViewGroup)?.removeView(state.cover)
        state.cover.setImageDrawable(null)
        // PixelCopy 回调前可能仍占用缓冲区，交由 GC 回收。
        try {
            if (!ModuleLifetime.isRetiring) state.completed(state.rebuilt)
        } finally {
            ModuleLifetime.finishTask()
        }
    }
}
