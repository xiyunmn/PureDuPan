package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.ViewTreeObserver
import com.xiyunmn.puredupan.hook.BuildConfig
import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAboutMeHookPoints
import java.util.Collections
import java.util.WeakHashMap

/** Keeps the host content and native collapsing scroll range aligned with the member card. */
internal object AboutMeBottomContentPositionHook {
    private const val TAG = "AboutMeBottomContentPositionHook"
    private const val SCROLL_VIEW_ID = "scroll_view"
    private const val COLLAPSIBLE_HEADER_ID = "collapsible_layout_title_root_view"
    private const val APPLY_DELAY_MS = 1500L
    private const val CACHE_SCHEMA_VERSION = 3
    private const val MIN_OFFSET_DP = -160
    private const val MAX_OFFSET_DP = 160

    private val hookState = HookState()
    private data class PositionCache(
        val baseTranslationY: Float,
    )

    private data class HeaderLayoutCache(
        val baseHeight: Int,
    )

    private data class ResolvedOffset(
        val signature: String,
        val offsetPx: Int,
        val fromPersistentCache: Boolean,
    )

    private val positionCache: MutableMap<View, PositionCache> =
        Collections.synchronizedMap(WeakHashMap())
    private val headerLayoutCache: MutableMap<View, HeaderLayoutCache> =
        Collections.synchronizedMap(WeakHashMap())
    fun hook(cl: ClassLoader) {
        val snapshot = HookSettings.settingsSnapshot()
        if (!isEnabled(snapshot)) {
            XposedCompat.logD("[$TAG] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        val fragmentClass = XposedCompat.findClassOrNull(
            BaiduAboutMeHookPoints.ABOUT_ME_BOTTOM_FRAGMENT,
            cl,
        ) ?: run {
            hookState.reset()
            XposedCompat.logW("[$TAG] AboutMeBottomFragment not found")
            return
        }
        val method = XposedCompat.findMethodOrNull(
            fragmentClass,
            "onViewCreated",
            View::class.java,
            Bundle::class.java,
        ) ?: run {
            hookState.reset()
            XposedCompat.logW("[$TAG] AboutMeBottomFragment.onViewCreated not found")
            return
        }

        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            val root = chain.args.firstOrNull() as? View
            root?.let(::schedulePositionApply)
            result
        }
        XposedCompat.logD("[$TAG] hook installed: ${fragmentClass.name}.${method.name}")
    }

    private fun schedulePositionApply(root: View) {
        val snapshot = HookSettings.settingsSnapshot()
        if (
            BaiduFeatureRuntime.isDomesticFamilyHost(root.context) &&
            snapshot.isMyPageContentAutoFollowMemberCardEnabled
        ) {
            scheduleDomesticCachedPositionApply(root)
            return
        }
        root.post { applyPosition(root, "cache", allowCalibration = false) }
        root.postDelayed(
            { applyPosition(root, "settled", allowCalibration = true) },
            APPLY_DELAY_MS,
        )
    }

    /**
     * Domestic hosts asynchronously restore the collapsing header after the cached position has
     * been applied. Keep the cached target stable before drawing until host initialization ends,
     * so the intermediate host height never becomes a visible frame. Samsung uses the same layout.
     */
    private fun scheduleDomesticCachedPositionApply(root: View) {
        lateinit var listener: ViewTreeObserver.OnPreDrawListener
        listener = ViewTreeObserver.OnPreDrawListener {
            maintainDomesticCachedPositionBeforeDraw(root)
        }
        root.viewTreeObserver.addOnPreDrawListener(listener)
        root.postDelayed(
            {
                applyPosition(root, "settled", allowCalibration = true)
                root.post {
                    root.viewTreeObserver
                        .takeIf { it.isAlive }
                        ?.removeOnPreDrawListener(listener)
                }
            },
            APPLY_DELAY_MS,
        )
    }

    private fun maintainDomesticCachedPositionBeforeDraw(root: View): Boolean {
        val snapshot = HookSettings.settingsSnapshot()
        if (!isEnabled(snapshot) || !snapshot.isMyPageContentAutoFollowMemberCardEnabled) return true
        val scrollView = findScrollView(root) ?: return true
        val density = root.resources?.displayMetrics?.density ?: return true
        val signature = cacheSignature(root, snapshot, density)
        val persisted = HookSettings.contentPositionCache(root.context, signature) ?: return true
        val header = findCollapsingHeader(root, scrollView) ?: return true
        val params = header.layoutParams ?: return true
        val cached = headerLayoutCache.getOrPut(header) {
            val baseHeight = params.height.takeIf { it > 0 }
                ?: header.height.takeIf { it > 0 }
                ?: header.measuredHeight.takeIf { it > 0 }
                ?: return true
            HeaderLayoutCache(baseHeight)
        }
        val targetHeight = (cached.baseHeight + persisted.offsetPx).coerceAtLeast(1)
        if (params.height == targetHeight && header.height == targetHeight) return true
        applyPosition(root, "pre-draw-cache", allowCalibration = false)
        return false
    }

    private fun applyPosition(root: View?, source: String, allowCalibration: Boolean) {
        root ?: return
        val snapshot = HookSettings.settingsSnapshot()
        if (!isEnabled(snapshot)) return
        val resolved = resolveOffset(root, snapshot, allowCalibration) ?: return
        val scrollView = findScrollView(root) ?: run {
            XposedCompat.logD(
                "[$TAG] scroll view not found via $source: root=${root.javaClass.name}, " +
                    "attached=${root.isAttachedToWindow}",
            )
            return
        }
        // Repeated application is safe because both paths derive from cached host baselines.
        val cached = positionCache.getOrPut(scrollView) {
            PositionCache(scrollView.translationY)
        }
        scrollView.translationY = cached.baseTranslationY
        val appliedNatively = if (snapshot.isMyPageContentAutoFollowMemberCardEnabled) {
            applyNativeCollapsingHeight(root, scrollView, resolved.offsetPx)
        } else {
            restoreNativeCollapsingHeight(root, scrollView)
            false
        }
        val targetTranslationY = if (appliedNatively) {
            cached.baseTranslationY
        } else {
            cached.baseTranslationY + resolved.offsetPx
        }
        scrollView.translationY = targetTranslationY

        if (!resolved.fromPersistentCache) {
            HookSettings.recordContentPositionCache(
                root.context,
                HookSettings.ContentPositionCache(resolved.signature, resolved.offsetPx),
            )
        }

        XposedCompat.logD(
            "[$TAG] content position applied via $source: offsetPx=${resolved.offsetPx}, " +
                "mode=${if (appliedNatively) "native-collapse" else "translation"}, " +
                "translationY=${cached.baseTranslationY}->$targetTranslationY",
        )
    }

    /**
     * The host keeps the member card beside a fixed-height CollapsingToolbarLayout and places
     * scroll_view below its AppBar. Growing that header by the card delta lets CoordinatorLayout
     * recalculate both the content position and the AppBar's native nested-scroll range.
     */
    private fun applyNativeCollapsingHeight(root: View, scrollView: View, offsetPx: Int): Boolean {
        val header = findCollapsingHeader(root, scrollView) ?: run {
            XposedCompat.logD("[$TAG] native collapsing header not found; using translation fallback")
            return false
        }
        val params = header.layoutParams ?: return false
        val cached = headerLayoutCache.getOrPut(header) {
            val baseHeight = params.height.takeIf { it > 0 }
                ?: header.height.takeIf { it > 0 }
                ?: header.measuredHeight.takeIf { it > 0 }
                ?: return false
            HeaderLayoutCache(baseHeight)
        }
        val targetHeight = (cached.baseHeight + offsetPx).coerceAtLeast(1)
        if (params.height != targetHeight) {
            params.height = targetHeight
            header.layoutParams = params
            header.requestLayout()
            (header.parent as? View)?.requestLayout()
            scrollView.requestLayout()
            (root.rootView ?: root).requestLayout()
        }
        XposedCompat.logD(
            "[$TAG] native collapsing height applied: ${cached.baseHeight}->$targetHeight",
        )
        return true
    }

    private fun restoreNativeCollapsingHeight(root: View, scrollView: View) {
        val header = findCollapsingHeader(root, scrollView) ?: return
        val cached = headerLayoutCache[header] ?: return
        val params = header.layoutParams ?: return
        if (params.height == cached.baseHeight) return
        params.height = cached.baseHeight
        header.layoutParams = params
        header.requestLayout()
        (header.parent as? View)?.requestLayout()
        scrollView.requestLayout()
        (root.rootView ?: root).requestLayout()
        XposedCompat.logD(
            "[$TAG] native collapsing height restored: ${cached.baseHeight}",
        )
    }

    private fun resolveOffset(
        view: View,
        snapshot: SettingsSnapshot,
        allowCalibration: Boolean,
    ): ResolvedOffset? {
        val density = view.resources?.displayMetrics?.density ?: return null
        val signature = cacheSignature(view, snapshot, density)
        HookSettings.contentPositionCache(view.context, signature)?.let { cached ->
            return ResolvedOffset(signature, cached.offsetPx, fromPersistentCache = true)
        }
        if (!allowCalibration) return null
        val offsetPx: Int? = when {
            snapshot.isMyPageContentAutoFollowMemberCardEnabled -> {
                autoFollowOffsetPx(view, snapshot, density)
            }
            snapshot.isMyPageContentManualOffsetEnabled -> {
                dpToPx(snapshot.myPageContentOffsetYDp.coerceIn(MIN_OFFSET_DP, MAX_OFFSET_DP), density)
            }
            else -> 0
        }
        return offsetPx?.let { ResolvedOffset(signature, it, fromPersistentCache = false) }
    }

    private fun cacheSignature(
        view: View,
        snapshot: SettingsSnapshot,
        density: Float,
    ): String {
        val hostVersion = hostVersionCode(view)
        val packageName = view.context?.packageName.orEmpty()
        return if (snapshot.isMyPageContentAutoFollowMemberCardEnabled) {
            val defaultHeightPx = view.context?.let(HookSettings::recordedMemberCardDefaultHeightPx) ?: 0
            listOf(
                CACHE_SCHEMA_VERSION,
                packageName,
                hostVersion,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                density.toBits(),
                "auto",
                snapshot.isMemberCardSizeAdjusted,
                snapshot.memberCardHeightDp,
                defaultHeightPx,
            ).joinToString(":")
        } else {
            listOf(
                CACHE_SCHEMA_VERSION,
                packageName,
                hostVersion,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                density.toBits(),
                "manual",
                snapshot.myPageContentOffsetYDp.coerceIn(MIN_OFFSET_DP, MAX_OFFSET_DP),
            ).joinToString(":")
        }
    }

    private fun hostVersionCode(view: View): Long {
        val context = view.context ?: return 0L
        return runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrDefault(0L)
    }

    private fun autoFollowOffsetPx(
        view: View,
        snapshot: SettingsSnapshot,
        density: Float,
    ): Int? {
        if (!snapshot.isMemberCardSizeAdjusted || snapshot.memberCardHeightDp <= 0) return 0
        val context = view.context ?: return 0
        val defaultHeightPx = HookSettings.recordedMemberCardDefaultHeightPx(context)
        if (defaultHeightPx <= 0) {
            XposedCompat.logD("[$TAG] auto-follow pending: default member-card height unavailable")
            return null
        }
        val targetHeightPx = dpToPx(snapshot.memberCardHeightDp, density)
        val minOffsetPx = dpToPx(MIN_OFFSET_DP, density)
        val maxOffsetPx = dpToPx(MAX_OFFSET_DP, density)
        return (targetHeightPx - defaultHeightPx).coerceIn(minOffsetPx, maxOffsetPx)
    }

    private fun findScrollView(root: View): View? {
        val context = root.context ?: return null
        val id = root.resources?.getIdentifier(SCROLL_VIEW_ID, "id", context.packageName) ?: 0
        if ((id != 0 && root.id == id) || root.javaClass.name.endsWith("NestedScrollView")) {
            return root
        }
        var current: ViewParent? = root.parent
        while (current is View) {
            if ((id != 0 && current.id == id) ||
                current.javaClass.name.endsWith("NestedScrollView")
            ) {
                return current
            }
            current = current.parent
        }
        return if (id != 0) (root.rootView ?: root).findViewById(id) else null
    }

    private fun findCollapsingHeader(root: View, scrollView: View): View? {
        var coordinatorRoot: View = scrollView
        var parent = coordinatorRoot.parent
        while (parent is View) {
            coordinatorRoot = parent
            if (coordinatorRoot.javaClass.name.endsWith("CoordinatorLayout")) break
            parent = coordinatorRoot.parent
        }
        findHostView(coordinatorRoot, COLLAPSIBLE_HEADER_ID)?.let { return it }
        return findDescendantByClassName(coordinatorRoot, "CollapsingToolbarLayout")
            ?: findDescendantByClassName(root.rootView ?: root, "CollapsingToolbarLayout")
    }

    private fun findHostView(root: View, idName: String): View? {
        val context = root.context ?: return null
        val id = root.resources?.getIdentifier(idName, "id", context.packageName) ?: 0
        return if (id != 0) root.findViewById(id) else null
    }

    private fun findDescendantByClassName(root: View, simpleName: String): View? {
        if (root.javaClass.name.endsWith(simpleName) && root.visibility == View.VISIBLE) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            findDescendantByClassName(root.getChildAt(index), simpleName)?.let { return it }
        }
        return null
    }

    private fun isEnabled(snapshot: SettingsSnapshot): Boolean {
        return snapshot.isMyPageCustomizeEnabled &&
            (
                snapshot.isMyPageContentAutoFollowMemberCardEnabled ||
                    snapshot.isMyPageContentManualOffsetEnabled
                )
    }

    private fun dpToPx(dp: Int, density: Float): Int = (dp * density).toInt()
}
