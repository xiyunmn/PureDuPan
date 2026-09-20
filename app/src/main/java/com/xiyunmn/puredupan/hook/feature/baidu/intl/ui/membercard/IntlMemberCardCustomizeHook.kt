package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.membercard

import android.app.Dialog
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.MemberCardBackgroundLayout
import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.ui.UiText
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.ZoomableImageView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.max

/**
 * 国际版会员卡（benefit-slot，AboutMeTopFragment）定制。
 *
 * 全部子功能挂 setCardUi(CenterConfig) 渲染入口：宿主在其中先设 ivVipImage + 主题，
 * 最后调 setCardText 写权益/续费/svip 文案，proceed() 后所有 binding 就绪。单次按明文资源 ID：
 * 隐藏权益/SVIP等级/续费（C），重套背景/尺寸/点击（A）。宿主每次数据变化重渲染即触发一次，
 * 仅背景使用布局后的单次 pre-draw，不阻止绘制，不按文案全树递归。
 *
 * 国际版 combinedLiveData 为 Pair<IdentityBean, CenterConfig>（无 PopupResponse 运营位），
 * 无 myCardHasOperation 逻辑门；operation 相关隐藏为 null-safe no-op（benefit-slot 布局无运营位）。
 * binding 字段名混淆，故隐藏一律用明文资源 ID，不用 binding 字段。
 */
internal object IntlMemberCardCustomizeHook {
    private const val MEMBER_CARD_ACTIVITY_CONTAINER_ID = "about_me_top"
    private const val MEMBER_CARD_ROOT_ID = "cl_aboutme_top"
    private const val MEMBER_CARD_USER_CENTER_ROOT_ID = "cl_aboutme_user_center"
    private const val MEMBER_CARD_BACKGROUND_ID = "iv_bg"
    private const val MEMBER_CARD_USER_CENTER_BACKGROUND_ID = "iv_user_center_bg"
    private const val MEMBER_CARD_OPERATION_ID = "operation_layout"
    private const val MEMBER_CARD_OPERATION_BACKGROUND_ID = "iv_bg_operation"
    private val MEMBER_CARD_OPERATION_CHILD_IDS = listOf(
        "card_operation_cover",
        "card_operation_cover_arrow",
        "card_operation_title",
        "card_operation_subtitle",
        "tv_tip",
    )
    private const val MEMBER_CARD_BENEFIT_ID = "cl_fifth_card"
    private const val MEMBER_CARD_FIRST_BENEFIT_ID = "cl_first_card"
    private const val MEMBER_CARD_SECOND_BENEFIT_ID = "cl_second_card"
    private const val MEMBER_CARD_THIRD_BENEFIT_ID = "cl_third_card"
    private const val MEMBER_CARD_BENEFIT_DIVIDER_ID = "view_line"
    private const val MEMBER_CARD_BENEFIT_BAR_ID = "ll_root"
    private const val MEMBER_CARD_USER_CENTER_BENEFIT_BAR_ID = "ll_card_root"
    private const val MEMBER_CARD_SVIP_LEVEL_ID = "iv_vip_image"
    private const val MEMBER_CARD_SVIP_NUMBER_ID = "tv_vip_number"
    private const val MEMBER_CARD_UNLOCK_SVIP_ROOT_ID = "rl_root"
    private const val MEMBER_CARD_SVIP_STATUS_ID = "tv_duration_content"
    private const val MEMBER_CARD_RENEW_BUTTON_ID = "tv_enter"
    private const val MEMBER_CARD_USER_CENTER_RENEW_BUTTON_ID = "tv_enter_user_center"
    private const val MEMBER_CARD_RENEW_TIP_ID = "tv_tip"
    private const val MEMBER_CARD_RENEW_DIVIDER_ID = "enter_line"
    private val MEMBER_CARD_ROOT_IDS = listOf(
        MEMBER_CARD_ROOT_ID,
        MEMBER_CARD_USER_CENTER_ROOT_ID,
    )
    private val MEMBER_CARD_BACKGROUND_IDS = listOf(
        MEMBER_CARD_BACKGROUND_ID,
        MEMBER_CARD_USER_CENTER_BACKGROUND_ID,
    )
    private val MEMBER_CARD_BENEFIT_BAR_IDS = listOf(
        MEMBER_CARD_BENEFIT_BAR_ID,
        MEMBER_CARD_USER_CENTER_BENEFIT_BAR_ID,
    )
    private val MEMBER_CARD_RENEW_BUTTON_IDS = listOf(
        MEMBER_CARD_RENEW_BUTTON_ID,
        MEMBER_CARD_USER_CENTER_RENEW_BUTTON_ID,
    )
    private val MEMBER_CARD_CLICK_TARGET_IDS = listOf(
        MEMBER_CARD_ACTIVITY_CONTAINER_ID,
        MEMBER_CARD_ROOT_ID,
        MEMBER_CARD_USER_CENTER_ROOT_ID,
        MEMBER_CARD_FIRST_BENEFIT_ID,
        MEMBER_CARD_SECOND_BENEFIT_ID,
        MEMBER_CARD_THIRD_BENEFIT_ID,
        MEMBER_CARD_USER_CENTER_BENEFIT_BAR_ID,
        MEMBER_CARD_UNLOCK_SVIP_ROOT_ID,
        MEMBER_CARD_RENEW_BUTTON_ID,
        MEMBER_CARD_USER_CENTER_RENEW_BUTTON_ID,
    )

    private data class AppliedBackground(
        val key: String,
        val bitmap: Bitmap,
    )
    private data class OriginalSize(
        val width: Int,
        val height: Int,
    )

    private val appliedBackgrounds = Collections.synchronizedMap(WeakHashMap<ImageView, AppliedBackground>())
    private val originalSizes = Collections.synchronizedMap(WeakHashMap<View, OriginalSize>())

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        val snapshot = HookSettings.memberCardSnapshot()
        if (!hasEnabledOption(snapshot)) {
            XposedCompat.log("[IntlMemberCardCustomizeHook] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        if (!hookState.markInstalled()) return

        try {
            val method = IntlAboutMeTopFragmentDexKitResolver.resolve(cl) ?: run {
                XposedCompat.log("[IntlMemberCardCustomizeHook] AboutMeTopFragment.setCardUi NOT RESOLVED")
                hookState.reset()
                return
            }
            hookCardRenderEntry(method)
            hookTopCreateView(method.declaringClass)
            XposedCompat.log("[IntlMemberCardCustomizeHook] hook INSTALLED")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[IntlMemberCardCustomizeHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    /**
     * 渲染入口：hook setCardUi(CenterConfig)，proceed() 后取 fragment.getView()，
     * 按资源 ID 套用隐藏/尺寸/点击；背景合并到布局后的单次 pre-draw，避免用旧尺寸裁剪。
     */
    private fun hookCardRenderEntry(method: java.lang.reflect.Method) {
        val mod = XposedCompat.module ?: return
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            try {
                val fragment = chain.thisObject
                val root = fragment?.let {
                    runCatching { it.javaClass.getMethod("getView").invoke(it) as? View }.getOrNull()
                }
                if (root != null) {
                    applyMemberCardCustomization(root)
                }
            } catch (e: Exception) {
                XposedCompat.logD("[IntlMemberCardCustomizeHook] render entry apply failed: ${e.message}")
            }
            result
        }
        XposedCompat.log(
            "[IntlMemberCardCustomizeHook] card render entry INSTALLED: " +
                "${method.declaringClass.name}.${method.name}",
        )
    }

    private fun hookTopCreateView(fragmentClass: Class<*>) {
        val mod = XposedCompat.module ?: return
        val method = XposedCompat.findMethodOrNull(
            fragmentClass,
            "onCreateView",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Bundle::class.java,
        ) ?: run {
            XposedCompat.logD("[IntlMemberCardCustomizeHook] top onCreateView not found")
            return
        }

        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            try {
                (result as? View)?.let(::hideTopSvipLevelBadge)
            } catch (e: Exception) {
                XposedCompat.logD("[IntlMemberCardCustomizeHook] top svip badge apply failed: ${e.message}")
            }
            result
        }
        XposedCompat.logD(
            "[IntlMemberCardCustomizeHook] top onCreateView hook installed: " +
                "${method.declaringClass.name}.${method.name}",
        )
    }

    private fun applyMemberCardCustomization(root: View) {
        val snapshot = HookSettings.memberCardSnapshot()
        if (!hasEnabledOption(snapshot)) return

        val resources = root.resources ?: return
        val packageName = root.context?.packageName ?: return
        val hostRoot = root.rootView ?: root
        val cardRoot = findFirstViewByEntryNames(root, resources, packageName, MEMBER_CARD_ROOT_IDS) ?: root
        val activityCardRoot = findViewByEntryName(
            hostRoot,
            resources,
            packageName,
            MEMBER_CARD_ACTIVITY_CONTAINER_ID,
        )?.takeIf { isAncestorOf(it, cardRoot) }
        applyCardClickBehavior(
            searchRoot = cardRoot,
            resources = resources,
            packageName = packageName,
            snapshot = snapshot,
            primaryTargets = listOf(activityCardRoot, cardRoot),
        )

        activityCardRoot?.let {
            applyCardSize(it, snapshot, recordDefault = true)
        }
        applyCardSize(cardRoot, snapshot, recordDefault = activityCardRoot == null)

        val background = findFirstViewByEntryNames(root, resources, packageName, MEMBER_CARD_BACKGROUND_IDS)
        if (background != null) {
            applyCardSize(background, snapshot)
            if (snapshot.isMemberCardBackgroundReplaced && background is ImageView) {
                MemberCardBackgroundLayout.apply(background, ::applyCustomBackground)
            }
        }

        if (snapshot.isMemberCardOperationHidden) {
            val operation = findViewByEntryName(root, resources, packageName, MEMBER_CARD_OPERATION_ID)
            if (operation != null && operation.visibility != View.GONE) {
                operation.visibility = View.GONE
                XposedCompat.logD("[IntlMemberCardCustomizeHook] member card operation hidden")
            }
            val operationBackground = findViewByEntryName(
                root,
                resources,
                packageName,
                MEMBER_CARD_OPERATION_BACKGROUND_ID,
            )
            if (operationBackground != null && operationBackground.visibility != View.GONE) {
                operationBackground.visibility = View.GONE
            }
            for (idName in MEMBER_CARD_OPERATION_CHILD_IDS) {
                val child = findViewByEntryName(root, resources, packageName, idName)
                if (child != null && child.visibility != View.GONE) {
                    child.visibility = View.GONE
                }
            }
        }

        if (snapshot.isMemberCardBenefitHidden) {
            val benefit = findViewByEntryName(root, resources, packageName, MEMBER_CARD_BENEFIT_ID)
            if (benefit != null && benefit.visibility != View.GONE) {
                benefit.visibility = View.GONE
                XposedCompat.logD("[IntlMemberCardCustomizeHook] member card benefit hidden")
            }
            val divider = findViewByEntryName(root, resources, packageName, MEMBER_CARD_BENEFIT_DIVIDER_ID)
            if (divider != null && divider.visibility != View.GONE) {
                divider.visibility = View.GONE
            }
        }

        if (snapshot.isMemberCardFirstBenefitHidden) {
            hideByEntryName(root, resources, packageName, MEMBER_CARD_FIRST_BENEFIT_ID) {
                XposedCompat.logD("[IntlMemberCardCustomizeHook] member card first benefit hidden")
            }
        }

        if (snapshot.isMemberCardSecondBenefitHidden) {
            hideByEntryName(root, resources, packageName, MEMBER_CARD_SECOND_BENEFIT_ID) {
                XposedCompat.logD("[IntlMemberCardCustomizeHook] member card second benefit hidden")
            }
        }

        if (snapshot.isMemberCardThirdBenefitHidden) {
            hideByEntryName(root, resources, packageName, MEMBER_CARD_THIRD_BENEFIT_ID) {
                XposedCompat.logD("[IntlMemberCardCustomizeHook] member card third benefit hidden")
            }
        }

        if (
            snapshot.isMemberCardFirstBenefitHidden &&
            snapshot.isMemberCardSecondBenefitHidden &&
            snapshot.isMemberCardThirdBenefitHidden
        ) {
            hideByEntryName(root, resources, packageName, MEMBER_CARD_BENEFIT_DIVIDER_ID) {
                XposedCompat.logD("[IntlMemberCardCustomizeHook] member card benefit divider hidden")
            }
            hideByEntryNames(root, resources, packageName, MEMBER_CARD_BENEFIT_BAR_IDS) {
                XposedCompat.logD("[IntlMemberCardCustomizeHook] member card benefit container hidden")
            }
        }

        if (snapshot.isMemberCardBenefitBarHidden) {
            hideByEntryNames(root, resources, packageName, MEMBER_CARD_BENEFIT_BAR_IDS) {
                XposedCompat.logD("[IntlMemberCardCustomizeHook] member card benefit bar hidden")
            }
        }

        if (snapshot.isIntlMemberCardSvipLevelHidden) {
            hideByEntryName(root, resources, packageName, MEMBER_CARD_SVIP_LEVEL_ID) {
                XposedCompat.logD("[IntlMemberCardCustomizeHook] intl svip level hidden")
            }
            hideByEntryName(root, resources, packageName, MEMBER_CARD_SVIP_NUMBER_ID)
            hideTopSvipLevelBadge(root)
        }

        if (snapshot.isMemberCardSvipStatusHidden) {
            hideByEntryName(root, resources, packageName, MEMBER_CARD_SVIP_STATUS_ID) {
                XposedCompat.logD("[IntlMemberCardCustomizeHook] member card svip status hidden")
            }
        }

        if (snapshot.isIntlMemberCardUpgradeButtonHidden) {
            val renewButton = findFirstViewByEntryNames(root, resources, packageName, MEMBER_CARD_RENEW_BUTTON_IDS)
            if (renewButton != null && renewButton.visibility != View.GONE) {
                renewButton.visibility = View.GONE
                XposedCompat.logD("[IntlMemberCardCustomizeHook] intl upgrade button hidden")
            }
            hideByEntryName(root, resources, packageName, MEMBER_CARD_RENEW_TIP_ID) {
                XposedCompat.logD("[IntlMemberCardCustomizeHook] upgrade price tip hidden")
            }
            val divider = findViewByEntryName(root, resources, packageName, MEMBER_CARD_RENEW_DIVIDER_ID)
            if (divider != null && divider.visibility != View.GONE) {
                divider.visibility = View.GONE
            }
        }
    }

    private fun hideTopSvipLevelBadge(root: View) {
        val snapshot = HookSettings.memberCardSnapshot()
        if (!snapshot.isMemberCardCustomizeEnabled || !snapshot.isIntlMemberCardSvipLevelHidden) return
        val resources = root.resources ?: return
        val packageName = root.context?.packageName ?: return
        hideByEntryName(root, resources, packageName, MEMBER_CARD_UNLOCK_SVIP_ROOT_ID) {
            XposedCompat.logD("[IntlMemberCardCustomizeHook] intl top svip level badge hidden")
        }
    }

    private fun hasEnabledOption(snapshot: SettingsSnapshot): Boolean {
        return snapshot.isMemberCardCustomizeEnabled &&
            (
                snapshot.isMemberCardBackgroundReplaced ||
                snapshot.isMemberCardSizeAdjusted ||
                    snapshot.isMemberCardOperationHidden ||
                    snapshot.isMemberCardBenefitHidden ||
                    snapshot.isMemberCardFirstBenefitHidden ||
                    snapshot.isMemberCardSecondBenefitHidden ||
                    snapshot.isMemberCardThirdBenefitHidden ||
                    snapshot.isMemberCardBenefitBarHidden ||
                    snapshot.isIntlMemberCardSvipLevelHidden ||
                    snapshot.isMemberCardSvipStatusHidden ||
                    snapshot.isIntlMemberCardUpgradeButtonHidden ||
                    snapshot.isMemberCardClickRemoved ||
                    snapshot.isMemberCardBackgroundViewedOnClick
            )
    }

    private fun applyCardClickBehavior(
        searchRoot: View,
        resources: android.content.res.Resources,
        packageName: String,
        snapshot: SettingsSnapshot,
        primaryTargets: List<View?>,
    ) {
        val clickTargets = linkedSetOf<View>()
        primaryTargets.filterNotNullTo(clickTargets)
        for (idName in MEMBER_CARD_CLICK_TARGET_IDS) {
            findViewByEntryName(searchRoot, resources, packageName, idName)?.let(clickTargets::add)
        }
        if (clickTargets.isEmpty()) return

        when {
            snapshot.isMemberCardClickRemoved -> {
                clickTargets.forEach { target ->
                    target.isEnabled = true
                    target.isClickable = true
                    target.setOnClickListener {
                        XposedCompat.logD("[IntlMemberCardCustomizeHook] member card click removed")
                    }
                }
            }
            snapshot.isMemberCardBackgroundViewedOnClick -> {
                snapshot.memberCardBackgroundUri?.takeIf { it.isNotBlank() } ?: return
                clickTargets.forEach { target ->
                    target.isEnabled = true
                    target.isClickable = true
                    target.setOnClickListener {
                        openCustomBackgroundImage(target)
                    }
                }
            }
        }
    }

    private fun openCustomBackgroundImage(view: View) {
        try {
            val context = view.context ?: return
            val snapshot = HookSettings.memberCardSnapshot()
            val uriString = snapshot.memberCardBackgroundUri?.takeIf { it.isNotBlank() } ?: return
            val bitmap = loadOriginalBackgroundPreviewBitmap(context, uriString) ?: run {
                Toast.makeText(
                    context,
                    UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_FAILED,
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }

            val density = context.resources.displayMetrics.density
            val metrics = context.resources.displayMetrics
            val imageView = ZoomableImageView(context, bitmap).apply {
                onLongPress = {
                    showOriginalBackgroundMenu(context, uriString)
                }
            }
            val container = FrameLayout(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                addView(
                    imageView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (metrics.heightPixels * 0.86f).toInt().coerceAtLeast((420 * density).toInt()),
                    ),
                )
            }

            val dialog = Dialog(context).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setContentView(container)
                setCanceledOnTouchOutside(true)
            }
            dialog.show()
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.45f)
                setLayout(
                    (metrics.widthPixels * 0.96f).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            XposedCompat.logD("[IntlMemberCardCustomizeHook] custom background preview opened")
        } catch (e: Exception) {
            XposedCompat.logW("[IntlMemberCardCustomizeHook] open custom background failed: ${e.message}")
        }
    }

    private fun showOriginalBackgroundMenu(
        context: Context,
        uriString: String,
    ) {
        val density = context.resources.displayMetrics.density
        val tokens = UiStyle.tokens(context)
        lateinit var dialog: Dialog
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(tokens.surface)
                cornerRadius = 18f * density
            }
            addView(createImageMenuItem(context, UiText.Settings.MEMBER_CARD_BACKGROUND_SAVE_TO_ALBUM, tokens) {
                saveOriginalMemberCardBackgroundToAlbum(context, uriString)
                dialog.dismiss()
            })
        }
        dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            setCanceledOnTouchOutside(true)
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.18f)
            setGravity(Gravity.CENTER)
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.66f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun createImageMenuItem(
        context: Context,
        text: String,
        tokens: UiStyle.Tokens,
        onClick: () -> Unit,
    ): View {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            this.text = text
            textSize = 17f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(tokens.textPrimary)
            includeFontPadding = false
            setPadding((24 * density).toInt(), 0, (24 * density).toInt(), 0)
            minHeight = (58 * density).toInt()
            setOnClickListener { onClick() }
        }
    }

    private fun hideByEntryName(
        root: View,
        resources: android.content.res.Resources,
        packageName: String,
        idName: String,
        onHidden: (() -> Unit)? = null,
    ) {
        val view = findViewByEntryName(root, resources, packageName, idName) ?: return
        if (view.visibility == View.GONE && view.alpha == 0f && !view.isEnabled && !view.isClickable) return
        view.visibility = View.GONE
        view.alpha = 0f
        view.isEnabled = false
        view.isClickable = false
        onHidden?.invoke()
    }

    private fun hideByEntryNames(
        root: View,
        resources: android.content.res.Resources,
        packageName: String,
        idNames: List<String>,
        onHidden: (() -> Unit)? = null,
    ) {
        var hidden = false
        for (idName in idNames) {
            val view = findViewByEntryName(root, resources, packageName, idName) ?: continue
            if (view.visibility == View.GONE && view.alpha == 0f && !view.isEnabled && !view.isClickable) {
                continue
            }
            view.visibility = View.GONE
            view.alpha = 0f
            view.isEnabled = false
            view.isClickable = false
            hidden = true
        }
        if (hidden) onHidden?.invoke()
    }

    private fun applyCardSize(
        background: View,
        snapshot: SettingsSnapshot,
        recordDefault: Boolean = false,
    ) {
        val params = background.layoutParams ?: return
        val original = originalSizes.getOrPut(background) {
            OriginalSize(params.width, params.height)
        }
        if (recordDefault) {
            recordDefaultMemberCardBackgroundSize(background, original)
        }

        val density = background.resources?.displayMetrics?.density ?: return
        val targetWidth = if (snapshot.isMemberCardSizeAdjusted && snapshot.memberCardWidthDp > 0) {
            dpToPx(snapshot.memberCardWidthDp, density)
        } else {
            original.width
        }
        val targetHeight = if (snapshot.isMemberCardSizeAdjusted && snapshot.memberCardHeightDp > 0) {
            dpToPx(snapshot.memberCardHeightDp, density)
        } else {
            original.height
        }

        if (params.width == targetWidth && params.height == targetHeight) return
        params.width = targetWidth
        params.height = targetHeight
        background.layoutParams = params
        background.requestLayout()
        XposedCompat.logD(
            "[IntlMemberCardCustomizeHook] member card size applied: width=${targetWidth}, height=${targetHeight}"
        )
    }

    private fun recordDefaultMemberCardBackgroundSize(
        background: View,
        original: OriginalSize,
    ) {
        val context = background.context ?: return
        if (HookSettings.hasRecordedMemberCardDefaultSize(context)) return
        val width = original.width
            .takeIf { it > 0 }
            ?: background.width.takeIf { it > 0 }
            ?: return
        val height = original.height
            .takeIf { it > 0 }
            ?: background.height.takeIf { it > 0 }
            ?: return
        HookSettings.recordMemberCardDefaultSize(context, width, height)
        XposedCompat.logD(
            "[IntlMemberCardCustomizeHook] default member card background size recorded: ${width}x${height}"
        )
    }

    private fun dpToPx(dp: Int, density: Float): Int {
        return (dp.coerceAtLeast(0) * density + 0.5f).toInt()
            .coerceAtLeast(ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun applyCustomBackground(background: ImageView) {
        val snapshot = HookSettings.memberCardSnapshot()
        if (!snapshot.isMemberCardCustomizeEnabled || !snapshot.isMemberCardBackgroundReplaced) return
        val uriString = snapshot.memberCardBackgroundUri?.takeIf { it.isNotBlank() } ?: return
        val blurRadius = snapshot.memberCardBackgroundBlurRadius.coerceIn(0, 25)
        val width = background.width.takeIf { it > 0 } ?: return
        val height = background.height.takeIf { it > 0 } ?: return
        val key = buildString {
            append(uriString)
            append('|').append(blurRadius)
            append('|').append(width).append('x').append(height)
            append('|').append(snapshot.memberCardBackgroundScalePercent)
            append('|').append(snapshot.memberCardBackgroundRotationDegrees)
            append('|').append(snapshot.memberCardBackgroundOffsetXPermille)
            append('|').append(snapshot.memberCardBackgroundOffsetYPermille)
        }
        val applied = appliedBackgrounds[background]
        val currentBitmap = (background.drawable as? BitmapDrawable)?.bitmap
        if (applied != null && applied.key == key && currentBitmap === applied.bitmap) {
            // The host may reset the scale type while retaining our bitmap.
            background.alpha = 1f
            background.scaleType = ImageView.ScaleType.FIT_XY
            return
        }

        val bitmap = if (applied != null && applied.key == key) {
            applied.bitmap
        } else {
            loadMemberCardBackground(background, uriString, snapshot, width, height) ?: return
        }
        background.alpha = 1f
        background.scaleType = ImageView.ScaleType.FIT_XY
        background.setImageBitmap(bitmap)
        appliedBackgrounds[background] = AppliedBackground(key, bitmap)
        XposedCompat.logD("[IntlMemberCardCustomizeHook] member card background replaced")
    }

    private fun loadMemberCardBackground(
        view: View,
        uriString: String,
        snapshot: SettingsSnapshot,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val context = view.context ?: return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val resolver = context.contentResolver ?: return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        try {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            val decodeTargetWidth = (targetWidth * snapshot.memberCardBackgroundScalePercent / 100f)
                .toInt()
                .coerceAtLeast(targetWidth)
            val decodeTargetHeight = (targetHeight * snapshot.memberCardBackgroundScalePercent / 100f)
                .toInt()
                .coerceAtLeast(targetHeight)
            options.inSampleSize = calculateInSampleSize(
                options.outWidth,
                options.outHeight,
                decodeTargetWidth,
                decodeTargetHeight,
            )
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null
            val transformed = renderMemberCardBackground(decoded, targetWidth, targetHeight, snapshot)
            if (decoded !== transformed && !decoded.isRecycled) {
                decoded.recycle()
            }
            val blurRadius = snapshot.memberCardBackgroundBlurRadius.coerceIn(0, 25)
            return if (blurRadius > 0) boxBlur(transformed, blurRadius) else transformed
        } catch (e: Exception) {
            XposedCompat.logW("[IntlMemberCardCustomizeHook] load custom background failed: ${e.message}")
            return null
        }
    }

    private fun renderMemberCardBackground(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        snapshot: SettingsSnapshot,
    ): Bitmap {
        val safeWidth = targetWidth.coerceAtLeast(1)
        val safeHeight = targetHeight.coerceAtLeast(1)
        val output = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.TRANSPARENT)
        val matrix = buildBackgroundMatrix(
            sourceWidth = source.width.toFloat().coerceAtLeast(1f),
            sourceHeight = source.height.toFloat().coerceAtLeast(1f),
            targetWidth = safeWidth.toFloat(),
            targetHeight = safeHeight.toFloat(),
            scalePercent = snapshot.memberCardBackgroundScalePercent,
            rotationDegrees = snapshot.memberCardBackgroundRotationDegrees,
            offsetXPermille = snapshot.memberCardBackgroundOffsetXPermille,
            offsetYPermille = snapshot.memberCardBackgroundOffsetYPermille,
        )
        canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun buildBackgroundMatrix(
        sourceWidth: Float,
        sourceHeight: Float,
        targetWidth: Float,
        targetHeight: Float,
        scalePercent: Int,
        rotationDegrees: Int,
        offsetXPermille: Int,
        offsetYPermille: Int,
    ): Matrix {
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        val rotatedWidth = if (normalizedRotation % 180 == 0) sourceWidth else sourceHeight
        val rotatedHeight = if (normalizedRotation % 180 == 0) sourceHeight else sourceWidth
        val coverScale = max(targetWidth / rotatedWidth, targetHeight / rotatedHeight)
        val userScale = scalePercent.coerceIn(100, 300) / 100f
        val drawScale = coverScale * userScale
        val overflowX = ((rotatedWidth * drawScale) - targetWidth).coerceAtLeast(0f) / 2f
        val overflowY = ((rotatedHeight * drawScale) - targetHeight).coerceAtLeast(0f) / 2f
        val offsetX = overflowX * offsetXPermille.coerceIn(-1000, 1000) / 1000f
        val offsetY = overflowY * offsetYPermille.coerceIn(-1000, 1000) / 1000f

        return Matrix().apply {
            postTranslate(-sourceWidth / 2f, -sourceHeight / 2f)
            postRotate(normalizedRotation.toFloat())
            postScale(drawScale, drawScale)
            postTranslate(targetWidth / 2f + offsetX, targetHeight / 2f + offsetY)
        }
    }

    private fun loadOriginalBackgroundPreviewBitmap(
        context: Context,
        uriString: String,
    ): Bitmap? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val resolver = context.contentResolver ?: return null
        val metrics = context.resources.displayMetrics
        val targetWidth = (metrics.widthPixels * 2).coerceAtMost(2400).coerceAtLeast(720)
        val targetHeight = (metrics.heightPixels * 2).coerceAtMost(2400).coerceAtLeast(720)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            options.inSampleSize = calculateInSampleSize(
                options.outWidth,
                options.outHeight,
                targetWidth,
                targetHeight,
            )
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            XposedCompat.logW("[IntlMemberCardCustomizeHook] load original background preview failed: ${e.message}")
            null
        }
    }

    private fun saveOriginalMemberCardBackgroundToAlbum(
        context: Context,
        uriString: String,
    ) {
        val success = runCatching {
            writeOriginalBackgroundToAlbum(context, uriString)
        }.onFailure {
            XposedCompat.logW("[IntlMemberCardCustomizeHook] save original background failed: ${it.message}")
        }.getOrDefault(false)
        Toast.makeText(
            context,
            if (success) {
                UiText.Settings.MEMBER_CARD_BACKGROUND_SAVED_TO_ALBUM
            } else {
                UiText.Settings.MEMBER_CARD_BACKGROUND_SAVE_TO_ALBUM_FAILED
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun writeOriginalBackgroundToAlbum(
        context: Context,
        uriString: String,
    ): Boolean {
        val resolver = context.contentResolver ?: return false
        val sourceUri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val bitmap = resolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return false
            return writeBitmapToAlbum(context, bitmap)
        }

        val mimeType = resolver.getType(sourceUri)
            ?.takeIf { it.startsWith("image/", ignoreCase = true) }
            ?: "image/jpeg"
        val displayName = queryDisplayName(resolver, sourceUri)
            ?.let { ensureImageExtension(it, mimeType) }
            ?: "member_card_background_${System.currentTimeMillis()}.${extensionForMimeType(mimeType)}"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PureDuPan")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val targetUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val saved = resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(targetUri)?.use { output ->
                    input.copyTo(output)
                }
            } != null
            if (saved) {
                resolver.update(
                    targetUri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            } else {
                resolver.delete(targetUri, null, null)
            }
            saved
        } catch (e: Exception) {
            resolver.delete(targetUri, null, null)
            throw e
        }
    }

    private fun queryDisplayName(
        resolver: ContentResolver,
        uri: Uri,
    ): String? {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
        }.getOrNull()
            ?.replace('/', '_')
            ?.replace('\\', '_')
            ?.takeIf { it.isNotBlank() }
    }

    private fun ensureImageExtension(
        displayName: String,
        mimeType: String,
    ): String {
        return if (displayName.substringAfterLast('.', "").isNotBlank()) {
            displayName
        } else {
            "$displayName.${extensionForMimeType(mimeType)}"
        }
    }

    private fun extensionForMimeType(mimeType: String): String {
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
    }

    private fun writeBitmapToAlbum(
        context: Context,
        bitmap: Bitmap,
    ): Boolean {
        val resolver = context.contentResolver ?: return false
        val fileName = "member_card_background_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PureDuPan")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val nowSeconds = System.currentTimeMillis() / 1000
                put(MediaStore.Images.Media.DATE_ADDED, nowSeconds)
                put(MediaStore.Images.Media.DATE_MODIFIED, nowSeconds)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val saved = resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            } == true
            if (saved) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                        null,
                        null,
                    )
                }
            } else {
                resolver.delete(uri, null, null)
            }
            saved
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        var sampleSize = 1
        while (
            sourceWidth / (sampleSize * 2) >= targetWidth &&
            sourceHeight / (sampleSize * 2) >= targetHeight
        ) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
        val safeRadius = radius.coerceIn(1, 25)
        val width = source.width
        val height = source.height
        if (width <= 1 || height <= 1) return source

        val input = IntArray(width * height)
        val temp = IntArray(width * height)
        val output = IntArray(width * height)
        source.getPixels(input, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (x in -safeRadius..safeRadius) {
                val pixel = input[y * width + x.coerceIn(0, width - 1)]
                alpha += pixel ushr 24
                red += (pixel shr 16) and 0xff
                green += (pixel shr 8) and 0xff
                blue += pixel and 0xff
            }
            for (x in 0 until width) {
                val count = safeRadius * 2 + 1
                temp[y * width + x] =
                    ((alpha / count) shl 24) or
                        ((red / count) shl 16) or
                        ((green / count) shl 8) or
                        (blue / count)

                val removePixel = input[y * width + (x - safeRadius).coerceIn(0, width - 1)]
                val addPixel = input[y * width + (x + safeRadius + 1).coerceIn(0, width - 1)]
                alpha += (addPixel ushr 24) - (removePixel ushr 24)
                red += ((addPixel shr 16) and 0xff) - ((removePixel shr 16) and 0xff)
                green += ((addPixel shr 8) and 0xff) - ((removePixel shr 8) and 0xff)
                blue += (addPixel and 0xff) - (removePixel and 0xff)
            }
        }

        for (x in 0 until width) {
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (y in -safeRadius..safeRadius) {
                val pixel = temp[y.coerceIn(0, height - 1) * width + x]
                alpha += pixel ushr 24
                red += (pixel shr 16) and 0xff
                green += (pixel shr 8) and 0xff
                blue += pixel and 0xff
            }
            for (y in 0 until height) {
                val count = safeRadius * 2 + 1
                output[y * width + x] =
                    ((alpha / count) shl 24) or
                        ((red / count) shl 16) or
                        ((green / count) shl 8) or
                        (blue / count)

                val removePixel = temp[(y - safeRadius).coerceIn(0, height - 1) * width + x]
                val addPixel = temp[(y + safeRadius + 1).coerceIn(0, height - 1) * width + x]
                alpha += (addPixel ushr 24) - (removePixel ushr 24)
                red += ((addPixel shr 16) and 0xff) - ((removePixel shr 16) and 0xff)
                green += ((addPixel shr 8) and 0xff) - ((removePixel shr 8) and 0xff)
                blue += (addPixel and 0xff) - (removePixel and 0xff)
            }
        }

        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun findViewByEntryName(
        root: View,
        resources: android.content.res.Resources,
        packageName: String,
        idName: String,
    ): View? {
        val id = resources.getIdentifier(idName, "id", packageName)
        if (id == 0) return null
        return root.findViewById(id)
    }

    private fun findFirstViewByEntryNames(
        root: View,
        resources: android.content.res.Resources,
        packageName: String,
        idNames: List<String>,
    ): View? {
        for (idName in idNames) {
            val view = findViewByEntryName(root, resources, packageName, idName)
            if (view != null) return view
        }
        return null
    }

    private fun isAncestorOf(ancestor: View, child: View): Boolean {
        var current: View? = child
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }
}
