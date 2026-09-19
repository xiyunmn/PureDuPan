package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduHomeCardHookPoints
import java.lang.reflect.Field
import java.lang.reflect.Method

/** 仅适配首页实例；测量、fling、nested scroll 和 unconsumed 仍由宿主执行。 */
internal class HomeFeedScrollCompat private constructor(
    private val layoutClass: Class<*>,
    private val contentField: Field,
    private val offsetGetter: Method,
    private val scrollBy: Method,
) {
    companion object {
        fun install(cl: ClassLoader): HomeFeedScrollCompat? {
            val mod = XposedCompat.module ?: return null
            return runCatching {
                val clazz = XposedCompat.findClassOrNull(BaiduHomeCardHookPoints.STICKY_NESTED_LAYOUT, cl)
                    ?: return null
                val content = clazz.getDeclaredField("contentView").apply { isAccessible = true }
                if (content.type != View::class.java) return null
                val offset = clazz.getDeclaredMethod("getStickyOffsetHeight").apply { isAccessible = true }
                if (offset.returnType != Int::class.javaPrimitiveType) return null
                val scroll = clazz.getDeclaredMethod(
                    "scrollByWithUnConsumed",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, IntArray::class.java,
                ).apply { isAccessible = true }
                if (scroll.returnType != Void.TYPE) return null
                val compat = HomeFeedScrollCompat(clazz, content, offset, scroll)
                mod.hook(offset).intercept { chain ->
                    val original = chain.proceed() as Int
                    val frame = content.get(chain.thisObject) as? ContentFrame
                    frame?.stickyOffset(original) ?: original
                }
                compat
            }.getOrElse {
                XposedCompat.logW("[HomeFeedScrollCompat] native layout contract unavailable: ${it.message}")
                null
            }
        }
    }

    fun attach(root: View) {
        val layout = findView(root, "sticky_nested_layout") as? ViewGroup ?: return
        if (!layoutClass.isInstance(layout)) return
        runCatching {
            val content = contentField.get(layout) as? FrameLayout ?: return
            if (content is ContentFrame || content.parent !== layout) return
            // 验证 binding 指向的原始容器。保留其 id、可见性及 Fragment 管理关系。
            if (content !== findView(root, "stickyContentView")) return
            val head = findView(root, "stickyHeadView") ?: return
            if (head.parent !== layout) return
            val nav = findView(root, "stickyNavView")?.takeIf { it.parent === layout }
            val index = layout.indexOfChild(content)
            val params = content.layoutParams
            val frame = ContentFrame(layout, head, nav, content)
            layout.removeViewAt(index)
            frame.addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            layout.addView(frame, index, params)
            contentField.set(layout, frame)
        }.onFailure {
            XposedCompat.logW("[HomeFeedScrollCompat] attach failed: ${it.message}")
        }
    }

    private fun findView(root: View, name: String): View? {
        val id = root.resources.getIdentifier(name, "id", root.context.packageName)
        return if (id == 0) null else root.findViewById(id)
    }

    private inner class ContentFrame(
        private val layout: ViewGroup,
        private val head: View,
        private val nav: View?,
        private val content: View,
    ) : FrameLayout(layout.context) {
        private val visibleBounds = Rect()

        fun stickyOffset(original: Int): Int {
            if (content.visibility == VISIBLE || !layout.getGlobalVisibleRect(visibleBounds)) {
                return original
            }
            // ComboScrollLayout 会平移整个 feed；measuredHeight 并不等于屏幕内可见高度。
            // 头部由原生 UNSPECIFIED 测量，包含最近列表和当前 Tab 的全部自然高度行。
            val navHeight = nav?.takeIf { it.visibility != GONE }?.height ?: 0
            val viewport = visibleBounds.height() - layout.paddingTop - layout.paddingBottom
            return HomeCardLayoutRules.hiddenFeedStickyOffset(head.measuredHeight, viewport, navHeight)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            // Tab 切换、折叠或数据变少时，通过同一个原生入口收回旧滚动位置。
            val offset = offsetGetter.invoke(layout) as Int
            if (layout.scrollY > (head.measuredHeight - offset).coerceAtLeast(0)) {
                scrollBy.invoke(layout, 0, 0, null)
            }
        }
    }
}
