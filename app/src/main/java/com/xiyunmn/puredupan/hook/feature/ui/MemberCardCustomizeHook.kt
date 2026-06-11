package com.xiyunmn.puredupan.hook.feature.ui

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
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.ui.UiText
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.ZoomableImageView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.max

/**
 * Customizes only the VIP card area in AboutMeTopFragmentHeteromo.
 */
object MemberCardCustomizeHook {
    private const val MEMBER_CARD_ROOT_ID = "cl_aboutme_top"
    private const val MEMBER_CARD_BACKGROUND_ID = "iv_bg"
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
    private const val MEMBER_CARD_BENEFIT_DIVIDER_ID = "view_line"
    private const val MEMBER_CARD_BENEFIT_BAR_ID = "ll_root"
    private const val MEMBER_CARD_SVIP_LEVEL_ID = "iv_vip_image"
    private const val MEMBER_CARD_SVIP_NUMBER_ID = "tv_vip_number"
    private const val MEMBER_CARD_UNLOCK_SVIP_ROOT_ID = "rl_root"
    private const val MEMBER_CARD_UNLOCK_SVIP_TEXT = "解锁SVIP"
    private const val MEMBER_CARD_SVIP_STATUS_ID = "tv_duration_content"
    private const val MEMBER_CARD_RENEW_BUTTON_ID = "tv_enter"
    private const val MEMBER_CARD_RENEW_DIVIDER_ID = "enter_line"

    private val attachedRoots = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
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

    @Volatile private var hooked = false

    internal fun hook(cl: ClassLoader) {
        val snapshot = ConfigManager.snapshot()
        if (!hasEnabledOption(snapshot)) {
            XposedCompat.log("[MemberCardCustomizeHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val fragmentClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.ABOUT_ME_TOP_FRAGMENT_HETEROMO,
                cl,
            ) ?: run {
                XposedCompat.log("[MemberCardCustomizeHook] AboutMeTopFragmentHeteromo class NOT FOUND")
                resetHooked()
                return
            }

            val method = XposedCompat.findMethodOrNull(
                fragmentClass,
                StableBaiduPanHookPoints.ABOUT_ME_TOP_FRAGMENT_ON_VIEW_CREATED_METHOD,
                View::class.java,
                Bundle::class.java,
            ) ?: run {
                XposedCompat.log("[MemberCardCustomizeHook] onViewCreated(View, Bundle) NOT FOUND")
                resetHooked()
                return
            }

            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                try {
                    attachMemberCardWatcher(chain.args.firstOrNull() as? View)
                } catch (t: Throwable) {
                    XposedCompat.logD("[MemberCardCustomizeHook] attach failed: ${t.message}")
                }
                result
            }
            hookOnCreateView(fragmentClass)

            XposedCompat.log("[MemberCardCustomizeHook] hook INSTALLED")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[MemberCardCustomizeHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookOnCreateView(fragmentClass: Class<*>) {
        val mod = XposedCompat.module ?: return
        val method = XposedCompat.findMethodOrNull(
            fragmentClass,
            "onCreateView",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Bundle::class.java,
        ) ?: run {
            XposedCompat.logD("[MemberCardCustomizeHook] onCreateView NOT FOUND, skipped")
            return
        }
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            try {
                val root = result as? View
                if (root != null) {
                    applyMemberCardCustomization(root)
                    attachMemberCardWatcher(root)
                }
            } catch (t: Throwable) {
                XposedCompat.logD("[MemberCardCustomizeHook] onCreateView apply failed: ${t.message}")
            }
            result
        }
    }

    private fun attachMemberCardWatcher(root: View?) {
        if (root == null) return
        if (!attachedRoots.add(root)) return

        root.post { applyMemberCardCustomization(root) }
        root.viewTreeObserver.addOnGlobalLayoutListener {
            try {
                applyMemberCardCustomization(root)
            } catch (_: Throwable) {
                // Keep host layout callbacks stable.
            }
        }
        root.viewTreeObserver.addOnPreDrawListener {
            try {
                applyMemberCardCustomization(root)
            } catch (_: Throwable) {
                // Keep host draw callbacks stable.
            }
            true
        }

        XposedCompat.log("[MemberCardCustomizeHook] watcher attached")
    }

    private fun applyMemberCardCustomization(root: View) {
        val snapshot = ConfigManager.snapshot()
        if (!hasEnabledOption(snapshot)) return

        val resources = root.resources ?: return
        val packageName = root.context?.packageName ?: return
        val cardRoot = findViewByEntryName(root, resources, packageName, MEMBER_CARD_ROOT_ID) ?: root
        applyCardClickBehavior(cardRoot, snapshot)

        val background = findViewByEntryName(root, resources, packageName, MEMBER_CARD_BACKGROUND_ID)
        if (background != null) {
            applyCardSize(background, snapshot)
            if (snapshot.isMemberCardBackgroundReplaced && background is ImageView) {
                applyCustomBackground(background, snapshot)
            }
        }

        if (snapshot.isMemberCardOperationHidden) {
            val operation = findViewByEntryName(root, resources, packageName, MEMBER_CARD_OPERATION_ID)
            if (operation != null && operation.visibility != View.GONE) {
                operation.visibility = View.GONE
                XposedCompat.logD("[MemberCardCustomizeHook] member card operation hidden")
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
                XposedCompat.logD("[MemberCardCustomizeHook] member card benefit hidden")
            }
            val divider = findViewByEntryName(root, resources, packageName, MEMBER_CARD_BENEFIT_DIVIDER_ID)
            if (divider != null && divider.visibility != View.GONE) {
                divider.visibility = View.GONE
            }
        }

        if (snapshot.isMemberCardBenefitBarHidden) {
            hideByEntryName(root, resources, packageName, MEMBER_CARD_BENEFIT_BAR_ID) {
                XposedCompat.logD("[MemberCardCustomizeHook] member card benefit bar hidden")
            }
        }

        if (snapshot.isMemberCardSvipLevelHidden) {
            hideByEntryName(root, resources, packageName, MEMBER_CARD_SVIP_LEVEL_ID) {
                XposedCompat.logD("[MemberCardCustomizeHook] member card svip level hidden")
            }
            hideByEntryName(root, resources, packageName, MEMBER_CARD_SVIP_NUMBER_ID)
            hideUnlockSvipEntry(root, resources, packageName)
        }

        if (snapshot.isMemberCardSvipStatusHidden) {
            hideByEntryName(root, resources, packageName, MEMBER_CARD_SVIP_STATUS_ID) {
                XposedCompat.logD("[MemberCardCustomizeHook] member card svip status hidden")
            }
        }

        if (snapshot.isMemberCardRenewButtonHidden) {
            val renewButton = findViewByEntryName(root, resources, packageName, MEMBER_CARD_RENEW_BUTTON_ID)
            if (renewButton != null && renewButton.visibility != View.GONE) {
                renewButton.visibility = View.GONE
                XposedCompat.logD("[MemberCardCustomizeHook] member card renew button hidden")
            }
            val divider = findViewByEntryName(root, resources, packageName, MEMBER_CARD_RENEW_DIVIDER_ID)
            if (divider != null && divider.visibility != View.GONE) {
                divider.visibility = View.GONE
            }
        }
    }

    private fun hasEnabledOption(snapshot: SettingsSnapshot): Boolean {
        return snapshot.isMemberCardCustomizeEnabled &&
            (
                snapshot.isMemberCardBackgroundReplaced ||
                    snapshot.isMemberCardSizeAdjusted ||
                    snapshot.isMemberCardOperationHidden ||
                    snapshot.isMemberCardBenefitHidden ||
                    snapshot.isMemberCardBenefitBarHidden ||
                    snapshot.isMemberCardSvipLevelHidden ||
                    snapshot.isMemberCardSvipStatusHidden ||
                    snapshot.isMemberCardRenewButtonHidden ||
                    snapshot.isMemberCardClickRemoved ||
                    snapshot.isMemberCardBackgroundViewedOnClick
            )
    }

    private fun applyCardClickBehavior(cardRoot: View, snapshot: SettingsSnapshot) {
        when {
            snapshot.isMemberCardClickRemoved -> {
                cardRoot.isClickable = true
                cardRoot.setOnClickListener {
                    XposedCompat.logD("[MemberCardCustomizeHook] member card click removed")
                }
            }
            snapshot.isMemberCardBackgroundViewedOnClick -> {
                snapshot.memberCardBackgroundUri?.takeIf { it.isNotBlank() } ?: return
                cardRoot.isClickable = true
                cardRoot.setOnClickListener {
                    openCustomBackgroundImage(cardRoot)
                }
            }
        }
    }

    private fun openCustomBackgroundImage(view: View) {
        try {
            val context = view.context ?: return
            val snapshot = ConfigManager.snapshot()
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
            XposedCompat.logD("[MemberCardCustomizeHook] custom background preview opened")
        } catch (t: Throwable) {
            XposedCompat.logW("[MemberCardCustomizeHook] open custom background failed: ${t.message}")
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

    private fun hideUnlockSvipEntry(
        root: View,
        resources: android.content.res.Resources,
        packageName: String,
    ) {
        val unlockRoot = findViewByEntryName(
            root,
            resources,
            packageName,
            MEMBER_CARD_UNLOCK_SVIP_ROOT_ID,
        )
        if (unlockRoot != null && containsText(unlockRoot, MEMBER_CARD_UNLOCK_SVIP_TEXT)) {
            hideView(unlockRoot) {
                XposedCompat.logD("[MemberCardCustomizeHook] member card unlock svip entry hidden")
            }
            return
        }
        if (hideContainerByText(root, MEMBER_CARD_UNLOCK_SVIP_TEXT, MEMBER_CARD_UNLOCK_SVIP_ROOT_ID)) {
            return
        }
        hideTextView(root, MEMBER_CARD_UNLOCK_SVIP_TEXT) {
            XposedCompat.logD("[MemberCardCustomizeHook] member card unlock svip text hidden")
        }
    }

    private fun hideView(view: View, onHidden: (() -> Unit)? = null) {
        if (view.visibility == View.GONE && view.alpha == 0f && !view.isEnabled && !view.isClickable) return
        view.visibility = View.GONE
        view.alpha = 0f
        view.isEnabled = false
        view.isClickable = false
        onHidden?.invoke()
    }

    private fun containsText(root: View, text: String): Boolean {
        if (root is TextView && root.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return true
        }
        if (root !is ViewGroup) return false
        for (index in 0 until root.childCount) {
            if (containsText(root.getChildAt(index), text)) return true
        }
        return false
    }

    private fun hideContainerByText(
        root: View,
        text: String,
        containerIdName: String,
    ): Boolean {
        val textView = findTextView(root, text) ?: return false
        var current = textView.parent as? View
        while (current != null && current !== root) {
            val entryName = runCatching {
                if (current.id != View.NO_ID) {
                    current.resources.getResourceEntryName(current.id)
                } else {
                    null
                }
            }.getOrNull()
            if (entryName == containerIdName) {
                hideView(current) {
                    XposedCompat.logD("[MemberCardCustomizeHook] member card unlock svip container hidden")
                }
                return true
            }
            current = current.parent as? View
        }
        val parent = textView.parent as? View
        if (parent != null && parent !== root) {
            hideView(parent) {
                XposedCompat.logD("[MemberCardCustomizeHook] member card unlock svip parent hidden")
            }
            return true
        }
        return false
    }

    private fun findTextView(root: View, text: String): TextView? {
        if (root is TextView && root.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return root
        }
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            val found = findTextView(root.getChildAt(index), text)
            if (found != null) return found
        }
        return null
    }

    private fun hideTextView(root: View, text: String, onHidden: (() -> Unit)? = null): Boolean {
        if (root is TextView && root.text?.toString()?.contains(text, ignoreCase = true) == true) {
            hideView(root, onHidden)
            return true
        }
        if (root !is ViewGroup) return false
        var hidden = false
        for (index in 0 until root.childCount) {
            hidden = hideTextView(root.getChildAt(index), text, onHidden) || hidden
        }
        return hidden
    }

    private fun applyCardSize(background: View, snapshot: SettingsSnapshot) {
        val params = background.layoutParams ?: return
        val original = originalSizes.getOrPut(background) {
            OriginalSize(params.width, params.height)
        }
        recordDefaultMemberCardBackgroundSize(background, original)

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
            "[MemberCardCustomizeHook] member card size applied: width=${targetWidth}, height=${targetHeight}"
        )
    }

    private fun recordDefaultMemberCardBackgroundSize(
        background: View,
        original: OriginalSize,
    ) {
        val context = background.context ?: return
        val prefs = ConfigManager.getModuleStatePrefs(context)
        if (
            prefs.getInt(ConfigManager.KEY_MEMBER_CARD_DEFAULT_WIDTH_PX, 0) > 0 &&
            prefs.getInt(ConfigManager.KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX, 0) > 0
        ) {
            return
        }
        val width = original.width
            .takeIf { it > 0 }
            ?: background.width.takeIf { it > 0 }
            ?: return
        val height = original.height
            .takeIf { it > 0 }
            ?: background.height.takeIf { it > 0 }
            ?: return
        prefs.edit()
            .putInt(ConfigManager.KEY_MEMBER_CARD_DEFAULT_WIDTH_PX, width)
            .putInt(ConfigManager.KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX, height)
            .apply()
        XposedCompat.logD(
            "[MemberCardCustomizeHook] default member card background size recorded: ${width}x${height}"
        )
    }

    private fun dpToPx(dp: Int, density: Float): Int {
        return (dp.coerceAtLeast(0) * density + 0.5f).toInt()
            .coerceAtLeast(ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun applyCustomBackground(background: ImageView, snapshot: SettingsSnapshot) {
        val uriString = snapshot.memberCardBackgroundUri?.takeIf { it.isNotBlank() } ?: return
        val blurRadius = snapshot.memberCardBackgroundBlurRadius.coerceIn(0, 25)
        val width = background.width.takeIf { it > 0 } ?: 1080
        val height = background.height.takeIf { it > 0 } ?: 360
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
        if (applied != null && applied.key == key && currentBitmap === applied.bitmap) return

        val bitmap = if (applied != null && applied.key == key) {
            applied.bitmap
        } else {
            loadMemberCardBackground(background, uriString, snapshot, width, height) ?: return
        }
        background.alpha = 1f
        background.scaleType = ImageView.ScaleType.FIT_XY
        background.setImageBitmap(bitmap)
        appliedBackgrounds[background] = AppliedBackground(key, bitmap)
        XposedCompat.logD("[MemberCardCustomizeHook] member card background replaced")
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
        } catch (t: Throwable) {
            XposedCompat.logW("[MemberCardCustomizeHook] load custom background failed: ${t.message}")
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
        } catch (t: Throwable) {
            XposedCompat.logW("[MemberCardCustomizeHook] load original background preview failed: ${t.message}")
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
            XposedCompat.logW("[MemberCardCustomizeHook] save original background failed: ${it.message}")
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
        } catch (t: Throwable) {
            resolver.delete(targetUri, null, null)
            throw t
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

    private fun saveMemberCardBackgroundToAlbum(
        context: Context,
        bitmap: Bitmap,
    ) {
        val success = runCatching {
            writeBitmapToAlbum(context, bitmap)
        }.onFailure {
            XposedCompat.logW("[MemberCardCustomizeHook] save custom background failed: ${it.message}")
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
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
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

    private fun tryMarkHooked(): Boolean = synchronized(this) {
        if (hooked) false else { hooked = true; true }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }
}
