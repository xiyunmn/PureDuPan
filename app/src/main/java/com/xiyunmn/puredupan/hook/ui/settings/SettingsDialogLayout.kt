package com.xiyunmn.puredupan.hook.ui.settings

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText

internal object SettingsDialogLayout {
    fun addVisibleRows(
        root: LinearLayout,
        rows: List<View>,
    ) {
        rows.forEach(root::addView)
    }

    fun addTitledSection(
        root: LinearLayout,
        context: Context,
        padding: Int,
        titleView: View,
        rows: List<View>,
        addDividerBefore: Boolean = false,
    ) {
        if (rows.isEmpty()) return
        if (addDividerBefore) {
            root.addView(createDivider(context, padding))
        }
        root.addView(titleView)
        addVisibleRows(root, rows)
    }

    fun createDialogScrollContainer(context: Context, content: View): ScrollView {
        return ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    fun createCustomHideWidgetSectionTitle(context: Context, padding: Int): TextView {
        return createSectionTitle(context, padding, UiText.Settings.CUSTOM_HIDE_WIDGET_SECTION_TITLE)
    }

    fun createCustomHideTextWidgetSectionTitle(context: Context, padding: Int): TextView {
        return createSectionTitle(context, padding, UiText.Settings.CUSTOM_HIDE_TEXT_WIDGET_SECTION_TITLE)
    }

    fun createCustomHideSectionSectionTitle(context: Context, padding: Int): TextView {
        return createSectionTitle(context, padding, UiText.Settings.CUSTOM_HIDE_SECTION_SECTION_TITLE)
    }

    fun createCustomSectionDisplaySectionTitle(context: Context, padding: Int): TextView {
        return createSectionTitle(context, padding, UiText.Settings.CUSTOM_SECTION_DISPLAY_SECTION_TITLE)
    }

    fun createMyPageContentPositionSectionTitle(context: Context, padding: Int): TextView {
        return createSectionTitle(context, padding, UiText.Settings.MY_PAGE_CONTENT_POSITION_SECTION_TITLE)
    }

    fun createPerformanceSectionTitle(
        context: Context,
        padding: Int,
        text: String,
    ): TextView {
        return createSectionTitle(context, padding, text)
    }

    fun createDivider(context: Context, padding: Int): View {
        val tokens = UiStyle.tokens(context)
        val density = context.resources.displayMetrics.density
        val divider = View(context)
        divider.setBackgroundColor(tokens.divider)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (0.8f * density).toInt().coerceAtLeast(1),
        )
        lp.setMargins(
            0,
            (padding * 0.4f).toInt(),
            0,
            (padding * 0.4f).toInt(),
        )
        divider.layoutParams = lp
        return divider
    }

    private fun createSectionTitle(
        context: Context,
        padding: Int,
        title: String,
    ): TextView {
        val tokens = UiStyle.tokens(context)
        return TextView(context).apply {
            text = title
            textSize = 12.5f
            letterSpacing = 0.04f
            setTextColor(tokens.accent)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setPadding(0, (padding * 0.45f).toInt(), 0, (padding * 0.2f).toInt())
        }
    }
}
