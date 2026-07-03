package com.xiyunmn.puredupan.hook.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.runtime.AutoDailySignInRuntime
import com.xiyunmn.puredupan.hook.settings.registry.SettingsDexKitState
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.settings.runtime.SettingsRuntimeSession
import com.xiyunmn.puredupan.hook.ui.HostThemeChangeDispatcher
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText
import com.xiyunmn.puredupan.hook.ui.showRestrictedFeatureWarningDialog

private const val RESTRICTED_FEATURE_UNLOCK_TAP_COUNT = 5
private const val DEXKIT_SUMMARY_REFRESH_MS = 1000L

internal object SettingsMainDialog {
    @Volatile private var versionTapCount = 0

    fun show(
        context: Context,
        initialScrollY: Int = 0,
        onChooseMemberCardBackground: () -> Unit,
        onReopenSettings: (Int) -> Unit,
        onRestartHost: () -> Unit,
    ) {
        try {
            val settingsSession = SettingsRuntimeSession.create(context)
            val prefs = settingsSession.prefs
            val texts = SettingsHostTextCatalog.forHostId(settingsSession.hostId)
            val density = context.resources.displayMetrics.density
            val padding = (20 * density).toInt()
            val defaultValues = topLevelDefaultValues(settingsSession)

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding / 2, padding, padding / 2)
            }

            val topLevelGroups = TopLevelSettingsItemsBuilder.topLevelGroups(
                primarySplashAdFeatureKey = settingsSession.primarySplashAdFeatureKey,
                isIntlHost = settingsSession.isIntlHost,
                restrictedUnlocked = SettingsUserState.areRestrictedFeaturesUnlocked,
                defaultValues = defaultValues,
                actionHandlers = TopLevelSettingsActionHandlers(
                    onHomeCustomizeClick = { PageCustomizeSettingsDialogs.showHome(context, prefs, settingsSession, texts) },
                    onFilePageCustomizeClick = {
                        PageCustomizeSettingsDialogs.showFilePage(context, prefs, settingsSession, texts)
                    },
                    onSearchPageCustomizeClick = {
                        PageCustomizeSettingsDialogs.showSearchPage(context, prefs, settingsSession, texts)
                    },
                    onSharePageCustomizeClick = {
                        PageCustomizeSettingsDialogs.showSharePage(context, prefs, settingsSession, texts)
                    },
                    onMyPageCustomizeClick = {
                        PageCustomizeSettingsDialogs.showMyPage(context, prefs, settingsSession, texts)
                    },
                    onMemberCardCustomizeClick = {
                        MemberCardCustomizeSettingsDialog.show(
                            context = context,
                            prefs = prefs,
                            settingsSession = settingsSession,
                            texts = texts,
                            onChooseBackground = onChooseMemberCardBackground,
                            onAdjustBackground = { uriString ->
                                MemberCardBackgroundEditorDialog.show(context, uriString)
                            },
                        )
                    },
                    onBottomBarCustomizeClick = {
                        BottomBarCustomizeSettingsDialog.show(context, prefs, settingsSession, texts)
                    },
                    onPerformanceOptimizeClick = {
                        PerformanceOptimizeSettingsDialog.show(context, prefs, settingsSession, texts)
                    },
                    onAutoDailySignInNowClick = {
                        if (!AutoDailySignInRuntime.triggerNow(context)) {
                            Toast.makeText(
                                context,
                                UiText.Settings.AUTO_DAILY_SIGN_IN_FAILED_TOAST,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ),
                texts = texts,
                isFeatureVisible = settingsSession::isFeatureVisible,
            )
            var requestDexKitSummaryRefresh: (() -> Unit)? = null
            val debugItems = TopLevelSettingsItemsBuilder.debugItems(
                hostPackageName = context.packageName,
                showDexKitStatus = settingsSession.showDexKitStatus,
                dexKitSummaryText = if (settingsSession.showDexKitStatus) {
                    SettingsDexKitState.summaryText(context)
                } else {
                    ""
                },
                actionHandlers = DebugSettingsActionHandlers(
                    onDexKitScanClick = {
                        SettingsDebugActions.executeDexKitScan(context)
                        requestDexKitSummaryRefresh?.invoke()
                    },
                    onDexKitDetailsClick = { SettingsDebugActions.showDexKitStatusDialog(context) },
                    onClearLogsClick = { SettingsDebugActions.showClearLogsConfirmDialog(context) },
                    onResetModuleSettingsClick = {
                        SettingsDebugActions.showResetModuleSettingsConfirmDialog(context) {
                            onRestartHost()
                        }
                    },
                ),
                texts = texts,
            )

            val groups = mutableListOf(
                SettingGroup(UiText.Settings.GROUP_CONTENT_BLOCK, topLevelGroups.contentBlockItems),
                SettingGroup(UiText.Settings.GROUP_UI_OPTIMIZE, topLevelGroups.uiSimplifyItems),
                SettingGroup(UiText.Settings.GROUP_THEME, topLevelGroups.themeItems),
                SettingGroup(UiText.Settings.GROUP_DEBUG, debugItems),
            )

            val runtimeSupportByKey = mutableMapOf<String?, SwitchRuntimeSupport>()
            val rowsByPrefKey = mutableMapOf<String, View>()
            val switchesByPrefKey = mutableMapOf<String, Switch>()
            fun supportOf(item: SwitchItem): SwitchRuntimeSupport {
                return runtimeSupportByKey.getOrPut(item.prefKey) {
                    resolveSwitchRuntimeSupport(item)
                }
            }

            var hasRenderedGroup = false
            groups.forEach { group ->
                val visibleItems = group.items.filter { it.visible }
                if (visibleItems.isEmpty()) {
                    return@forEach
                }
                if (hasRenderedGroup) {
                    val gap = View(context)
                    gap.layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (12 * density).toInt(),
                    )
                    root.addView(gap)
                }

                val tokens = UiStyle.tokens(context)
                val headerLabel = TextView(context).apply {
                    text = group.name
                    textSize = 12.5f
                    letterSpacing = 0.04f
                    setTextColor(tokens.accent)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setPadding(0, (padding * 0.7f).toInt(), 0, (padding * 0.35f).toInt())
                }
                root.addView(headerLabel)

                visibleItems.forEach { item ->
                    val support = supportOf(item)
                    val finalLabel = if (!support.supported) {
                        UiText.Settings.withUnsupportedSuffix(item.label)
                    } else if (support.partial) {
                        UiText.Settings.withPartialSuffix(item.label)
                    } else {
                        item.label
                    }
                    val finalDesc = if (!support.note.isNullOrBlank()) {
                        val base = item.description
                        if (base.isNullOrBlank()) support.note else "$base\n${support.note}"
                    } else {
                        item.description
                    }

                    val rowView = SettingsSwitchRows.create(
                        context,
                        prefs,
                        finalLabel,
                        finalDesc,
                        item.prefKey,
                        padding,
                        support.supported,
                        if (support.supported) item.defaultValue else false,
                        item.actionIcon,
                        item.onActionClick,
                        item.linkedPrefKeys,
                        item.showSwitch,
                        item.actionButtonText,
                        item.statusBadgeText,
                        item.onStatusBadgeClick,
                    )
                    root.addView(rowView)
                    item.prefKey?.let { key ->
                        rowsByPrefKey[key] = rowView
                        SettingsSwitchRows.findSwitchView(rowView)?.let { switchView ->
                            switchesByPrefKey[key] = switchView
                        }
                    }
                }
                hasRenderedGroup = true
            }

            var settingsDialog: AlertDialog? = null
            val versionClickListener = if (!SettingsUserState.areRestrictedFeaturesUnlocked) {
                View.OnClickListener {
                    versionTapCount++
                    if (versionTapCount >= RESTRICTED_FEATURE_UNLOCK_TAP_COUNT) {
                        versionTapCount = 0
                        showRestrictedFeatureWarningDialog(context) {
                            SettingsUserState.setRestrictedFeaturesUnlocked(context, true)
                            settingsDialog?.dismiss()
                            Handler(Looper.getMainLooper()).postDelayed({
                                onReopenSettings(0)
                            }, 100)
                            Toast.makeText(
                                context,
                                UiText.Settings.RESTRICTED_FEATURE_UNLOCKED_TEXT,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            } else null

            root.addView(SettingsDialogLayout.createDivider(context, padding))
            val showDeviceFingerprint = {
                prefs.getBoolean(SettingsUserState.KEY_SHOW_DEVICE_FINGERPRINT, false)
            }
            val aboutSection = SettingsAboutSection.create(
                context = context,
                padding = padding,
                hostId = settingsSession.hostId,
                versionClickListener = versionClickListener,
                showDeviceFingerprint = showDeviceFingerprint,
                setShowDeviceFingerprint = { enabled ->
                    prefs.edit()
                        .putBoolean(SettingsUserState.KEY_SHOW_DEVICE_FINGERPRINT, enabled)
                        .commit()
                },
            )
            root.addView(aboutSection.root)

            val scrollContainer = ScrollView(context).apply {
                addView(
                    root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            if (initialScrollY > 0) {
                scrollContainer.post {
                    scrollContainer.scrollTo(0, initialScrollY)
                }
            }

            val tokensTitle = UiStyle.tokens(context)
            val titleView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding / 2)

                addView(TextView(context).apply {
                    text = UiText.Settings.MODULE_SETTINGS
                    textSize = 22f
                    letterSpacing = 0.02f
                    setTextColor(tokensTitle.textPrimary)
                    typeface = Typeface.DEFAULT_BOLD
                })

                val brandTag = TextView(context).apply {
                    text = UiText.Settings.BRAND_TAG
                    textSize = 11.5f
                    letterSpacing = 0.06f
                    typeface = Typeface.MONOSPACE
                    setTextColor(tokensTitle.textMuted)
                    setPadding(0, (2 * density).toInt(), 0, 0)
                }
                addView(brandTag)
                UiStyle.animateBrandTagShimmer(brandTag)
            }

            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setCustomTitle(titleView)
                .setView(scrollContainer)
                .setPositiveButton(UiText.Settings.SAVE_AND_RESTART) { _, _ ->
                    Toast.makeText(
                        context,
                        UiText.Settings.SETTINGS_SAVED_RESTARTING,
                        Toast.LENGTH_SHORT,
                    ).show()
                    onRestartHost()
                }
                .create()
            settingsDialog = dialog

            dialog.show()
            val unregisterDeviceFingerprintVisibilityRefresh = bindDeviceFingerprintVisibilityRefresh(
                prefs = prefs,
                refresh = aboutSection.refreshDeviceFingerprintDescription,
            )
            val dexKitSummaryRefresh = bindDexKitSummaryRefresh(
                context = context,
                prefs = prefs,
                showDexKitStatus = settingsSession.showDexKitStatus,
                dexKitRow = rowsByPrefKey[SettingsUserState.KEY_DEXKIT_STATUS],
            )
            requestDexKitSummaryRefresh = dexKitSummaryRefresh?.requestRefresh
            val unregisterNightModeDependency = bindIntlNightModeDependency(
                prefs = prefs,
                isIntlHost = settingsSession.isIntlHost,
                rowsByPrefKey = rowsByPrefKey,
                switchesByPrefKey = switchesByPrefKey,
            )
            var unregisterThemeListener: (() -> Unit)? = null
            var reopeningForTheme = false
            unregisterThemeListener = HostThemeChangeDispatcher.register { reason ->
                if (reopeningForTheme || !dialog.isShowing) return@register
                val activity = context as? Activity
                if (activity != null && (activity.isFinishing || activity.isDestroyed)) return@register

                reopeningForTheme = true
                val scrollY = scrollContainer.scrollY
                dialog.window?.decorView?.post {
                    try {
                        unregisterThemeListener?.invoke()
                        unregisterThemeListener = null
                        dialog.dismiss()
                        if (activity == null || (!activity.isFinishing && !activity.isDestroyed)) {
                            onReopenSettings(scrollY)
                        }
                        XposedCompat.logD("[SettingsMainDialog] settings dialog recreated after host theme change: $reason")
                    } catch (t: Throwable) {
                        XposedCompat.logW("[SettingsMainDialog] recreate after theme change failed: ${t.message}")
                    }
                }
            }
            dialog.setOnDismissListener {
                unregisterDeviceFingerprintVisibilityRefresh()
                dexKitSummaryRefresh?.unregister?.invoke()
                unregisterNightModeDependency?.invoke()
                unregisterThemeListener?.invoke()
                unregisterThemeListener = null
            }
            dialog.window?.let { window ->
                val windowDensity = context.resources.displayMetrics.density
                SettingsDialogWindows.applyCardStyle(window, windowDensity)
                UiStyle.animateDialogEntry(window.decorView, windowDensity)
            }
        } catch (t: Throwable) {
            XposedCompat.log("[SettingsMainDialog] FAILED to show settings dialog: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private data class DexKitSummaryRefreshBinding(
        val requestRefresh: () -> Unit,
        val unregister: () -> Unit,
    )

    private fun bindDexKitSummaryRefresh(
        context: Context,
        prefs: SharedPreferences,
        showDexKitStatus: Boolean,
        dexKitRow: View?,
    ): DexKitSummaryRefreshBinding? {
        if (!showDexKitStatus) return null
        val summaryView = dexKitRow?.let(SettingsSwitchRows::findStatusBadgeTextView) ?: return null
        val mainHandler = Handler(Looper.getMainLooper())

        fun refreshSummary() {
            summaryView.text = SettingsSwitchRows.emphasizeDexKitStatusBadge(
                dexKitStatusBadgeText(SettingsDexKitState.summaryText(context)),
                UiStyle.tokens(context),
            )
        }

        val refreshRunnable = object : Runnable {
            override fun run() {
                refreshSummary()
                if (SettingsDexKitState.shouldContinueStatusRefresh(context)) {
                    mainHandler.postDelayed(this, DEXKIT_SUMMARY_REFRESH_MS)
                }
            }
        }

        fun scheduleRefresh(forceNextTick: Boolean = false) {
            mainHandler.removeCallbacks(refreshRunnable)
            refreshSummary()
            if (forceNextTick || SettingsDexKitState.shouldContinueStatusRefresh(context)) {
                mainHandler.postDelayed(refreshRunnable, DEXKIT_SUMMARY_REFRESH_MS)
            }
        }

        scheduleRefresh()

        return DexKitSummaryRefreshBinding(
            requestRefresh = { scheduleRefresh(forceNextTick = true) },
            unregister = { mainHandler.removeCallbacks(refreshRunnable) },
        )
    }

    private fun dexKitStatusBadgeText(summary: String): String {
        val parts = summary.split("/")
        val formatted = if (parts.size == 2) {
            "${parts[0].trim()} / ${parts[1].trim()}"
        } else {
            summary
        }
        return "${UiText.Settings.DEXKIT_STATUS_BADGE_PREFIX}: $formatted"
    }

    private fun topLevelDefaultValues(settingsSession: SettingsRuntimeSession): TopLevelSettingsDefaultValues {
        val prefs = settingsSession.prefs
        return TopLevelSettingsDefaultValues(
            homeCustomize = PageCustomizeSettingsItemsBuilder.hasEnabledHomeCustomizeOption(
                prefs = prefs,
                isFeatureVisible = settingsSession::isFeatureVisible,
            ),
            filePageCustomize = PageCustomizeSettingsItemsBuilder.hasEnabledFilePageCustomizeOption(
                isFeatureVisible = settingsSession::isFeatureVisible,
                isChecked = { key -> prefs.getBoolean(key, false) },
            ),
            searchPageCustomize = PageCustomizeSettingsItemsBuilder.hasEnabledSearchPageCustomizeOption(
                isFeatureVisible = settingsSession::isFeatureVisible,
                isChecked = { key -> prefs.getBoolean(key, false) },
            ),
            sharePageCustomize = PageCustomizeSettingsItemsBuilder.hasEnabledSharePageCustomizeOption(
                isFeatureVisible = settingsSession::isFeatureVisible,
                isChecked = { key -> prefs.getBoolean(key, false) },
            ),
            myPageCustomize = PageCustomizeSettingsItemsBuilder.hasEnabledMyPageCustomizeOption(
                isFeatureVisible = settingsSession::isFeatureVisible,
                isChecked = { key -> prefs.getBoolean(key, false) },
            ),
            memberCardCustomize = MemberCardSettingsItemsBuilder.hasEnabledMemberCardCustomizeOption(
                prefs = prefs,
                memberCardLayoutMode = settingsSession.memberCardLayoutMode,
                isFeatureVisible = settingsSession::isFeatureVisible,
            ),
            bottomBarCustomize = BottomBarSettingsItemsBuilder.hasEnabledBottomBarCustomizeOption(
                prefs = prefs,
                isFeatureVisible = settingsSession::isFeatureVisible,
            ),
            performanceOptimize = PerformanceSettingsItemsBuilder.hasEnabledPerformanceOptimizeOption(
                prefs = prefs,
                isFeatureVisible = settingsSession::isFeatureVisible,
            ),
        )
    }

    private fun resolveSwitchRuntimeSupport(item: SwitchItem): SwitchRuntimeSupport {
        return SwitchRuntimeSupport(
            supported = item.supported,
            partial = false,
            note = null,
        )
    }

    private fun bindDeviceFingerprintVisibilityRefresh(
        prefs: SharedPreferences,
        refresh: () -> Unit,
    ): () -> Unit {
        val mainHandler = Handler(Looper.getMainLooper())
        val refreshRunnable = Runnable { refresh() }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == SettingsUserState.KEY_SHOW_DEVICE_FINGERPRINT) {
                mainHandler.post(refreshRunnable)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        refresh()

        return {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            mainHandler.removeCallbacks(refreshRunnable)
        }
    }

    private fun bindIntlNightModeDependency(
        prefs: SharedPreferences,
        isIntlHost: Boolean,
        rowsByPrefKey: Map<String, View>,
        switchesByPrefKey: Map<String, Switch>,
    ): (() -> Unit)? {
        if (!isIntlHost) return null

        val supportKey = SettingsUserState.KEY_ENABLE_NIGHT_MODE_SUPPORT
        val followKey = SettingsUserState.KEY_FOLLOW_SYSTEM_NIGHT_MODE
        val followRow = rowsByPrefKey[followKey] ?: return null
        val followSwitch = switchesByPrefKey[followKey] ?: return null
        val supportSwitch = switchesByPrefKey[supportKey] ?: return null
        val mainHandler = Handler(Looper.getMainLooper())

        fun isSupportEnabled(): Boolean {
            return supportSwitch.isChecked || prefs.getBoolean(supportKey, false)
        }

        fun refreshDependency() {
            val supportEnabled = isSupportEnabled()
            SettingsSwitchRows.setRowEnabled(followRow, supportEnabled)
            if (!supportEnabled) {
                if (prefs.getBoolean(followKey, false)) {
                    prefs.edit().putBoolean(followKey, false).commit()
                }
                if (followSwitch.isChecked) {
                    followSwitch.isChecked = false
                }
            }
        }

        val refreshRunnable = Runnable { refreshDependency() }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == supportKey || key == followKey) {
                mainHandler.post(refreshRunnable)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        refreshDependency()

        return {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            mainHandler.removeCallbacks(refreshRunnable)
        }
    }
}
