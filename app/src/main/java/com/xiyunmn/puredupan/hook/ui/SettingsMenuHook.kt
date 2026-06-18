package com.xiyunmn.puredupan.hook.ui

import android.app.AlertDialog
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.Constants
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.host.HostFeatureAvailabilityRegistry
import com.xiyunmn.puredupan.hook.host.HostRegistry
import com.xiyunmn.puredupan.hook.ui.settings.IntSliderControl
import com.xiyunmn.puredupan.hook.ui.settings.MemberCardBackgroundImageControl
import com.xiyunmn.puredupan.hook.ui.settings.SettingGroup
import com.xiyunmn.puredupan.hook.ui.settings.SettingsDebugActions
import com.xiyunmn.puredupan.hook.ui.settings.SwitchItem
import com.xiyunmn.puredupan.hook.ui.settings.SwitchRuntimeSupport
import com.xiyunmn.puredupan.hook.ui.settings.VersionDisplayInfo

private const val MEMBER_CARD_BACKGROUND_BLUR_MIN = 0
private const val MEMBER_CARD_BACKGROUND_BLUR_MAX = 25
private const val MEMBER_CARD_SIZE_WIDTH_MIN = 0
private const val MEMBER_CARD_SIZE_WIDTH_MAX = 420
private const val MEMBER_CARD_SIZE_HEIGHT_MIN = 0
private const val MEMBER_CARD_SIZE_HEIGHT_MAX = 280
private const val RESTRICTED_FEATURE_UNLOCK_TAP_COUNT = 5
private const val DISCLAIMER_COUNTDOWN_SECONDS = 5
private const val RESTRICTED_FEATURE_WARNING_COUNTDOWN_SECONDS = 3
internal const val REQUEST_MEMBER_CARD_BACKGROUND_IMAGE = 0x4D31

object SettingsMenuHook {
    @Volatile private var versionTapCount = 0
    @Volatile private var memberCardBackgroundSelectionListener: ((String) -> Unit)? = null

    internal fun launchMemberCardBackgroundPicker(context: Context) {
        try {
            val activity = context as? Activity ?: run {
                Toast.makeText(context, UiText.Settings.MEMBER_CARD_BACKGROUND_PICK_FAILED, Toast.LENGTH_SHORT).show()
                return
            }
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
            activity.startActivityForResult(intent, REQUEST_MEMBER_CARD_BACKGROUND_IMAGE)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] launch background picker failed: ${t.message}")
            Toast.makeText(context, UiText.Settings.MEMBER_CARD_BACKGROUND_PICK_FAILED, Toast.LENGTH_SHORT).show()
        }
    }

    internal fun handleMemberCardBackgroundImageResult(
        context: Context?,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ): Boolean {
        if (requestCode != REQUEST_MEMBER_CARD_BACKGROUND_IMAGE) return false
        if (context == null || resultCode != Activity.RESULT_OK) return true

        val uri = data?.data ?: return true
        try {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (t: Throwable) {
            XposedCompat.logD("[SettingsMenuHook] persist image uri permission failed: ${t.message}")
        }

        ConfigManager.getPrefs(context).edit()
            .putBoolean(ConfigManager.KEY_MEMBER_CARD_CUSTOMIZE, true)
            .putBoolean(ConfigManager.KEY_REPLACE_MEMBER_CARD_BACKGROUND, true)
            .putString(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_URI, uri.toString())
            .putInt(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT, 100)
            .putInt(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES, 0)
            .putInt(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE, 0)
            .putInt(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE, 0)
            .apply()

        memberCardBackgroundSelectionListener?.invoke(uri.toString())
        showMemberCardBackgroundEditorDialog(context, uri.toString())
        Toast.makeText(
            context,
            UiText.Settings.withRestartHint(UiText.Settings.MEMBER_CARD_BACKGROUND_PICKED),
            Toast.LENGTH_SHORT,
        ).show()
        return true
    }

    internal fun showModuleSettingsDialog(
        context: Context,
        classLoader: ClassLoader?,
        initialScrollY: Int = 0,
    ) {
        // 首次使用检查免责声明
        if (!ConfigManager.isDisclaimerAccepted(context)) {
            showDisclaimerDialog(context) {
                ConfigManager.setDisclaimerAccepted(context)
                showModuleSettingsDialogInternal(context, classLoader, initialScrollY)
            }
            return
        }
        showModuleSettingsDialogInternal(context, classLoader, initialScrollY)
    }

    private fun showModuleSettingsDialogInternal(
        context: Context,
        classLoader: ClassLoader?,
        initialScrollY: Int = 0,
    ) {
        try {
            val prefs = ConfigManager.getPrefs(context)
            ConfigManager.applyFeatureAvailability(
                context = context,
                featureStatusMap = HostFeatureAvailabilityRegistry.featureStatusMapFor(context.packageName),
                refreshRuntime = true,
            )
            val density = context.resources.displayMetrics.density
            val padding = (20 * density).toInt()
            val homeCustomizeDefault = hasAnyHomeCustomizeOptionEnabled(context, prefs)
            val sharePageCustomizeDefault = hasAnySharePageCustomizeOptionEnabled(context, prefs)
            val myPageCustomizeDefault = hasAnyMyPageCustomizeOptionEnabled(context, prefs)
            val memberCardCustomizeDefault = hasAnyMemberCardCustomizeOptionEnabled(context, prefs)
            val bottomBarCustomizeDefault = hasAnyBottomBarCustomizeOptionEnabled(context, prefs)
            val performanceOptimizeDefault = hasAnyPerformanceOptimizeOptionEnabled(context, prefs)

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding / 2, padding, padding / 2)
            }

            // 分组1：广告拦截
            val adBlockItems = mutableListOf<SwitchItem>()
            val hostCapabilities = hostCapabilities(context)

            // 仅解锁后显示
            if (ConfigManager.areRestrictedFeaturesUnlocked) {
                adBlockItems.add(
                    SwitchItem(
                        UiText.Settings.ACCELERATE_INTL_SPLASH_STARTUP_LABEL,
                        UiText.Settings.ACCELERATE_INTL_SPLASH_STARTUP_DESC,
                        ConfigManager.KEY_ACCELERATE_INTL_SPLASH_STARTUP,
                        hostCapabilities.supportsLaunchHandoffOptimize,
                        false,
                        visible = isFeatureVisible(context, ConfigManager.KEY_ACCELERATE_INTL_SPLASH_STARTUP),
                    )
                )
                if (!hostCapabilities.supportsStandaloneHotStartSplashRemove) {
                    adBlockItems.add(
                        SwitchItem(
                            UiText.Settings.BLOCK_SPLASH_INTERSTITIAL_LABEL,
                            UiText.Settings.BLOCK_SPLASH_INTERSTITIAL_DESC,
                            ConfigManager.KEY_BLOCK_SPLASH_INTERSTITIAL,
                            hostCapabilities.supportsHotStartSplashAd,
                            false,
                            visible = isFeatureVisible(context, ConfigManager.KEY_BLOCK_SPLASH_INTERSTITIAL),
                        )
                    )
                } else {
                    adBlockItems.add(
                        SwitchItem(
                            UiText.Settings.REMOVE_HOT_START_SPLASH_LABEL,
                            UiText.Settings.REMOVE_HOT_START_SPLASH_DESC,
                            ConfigManager.KEY_REMOVE_HOT_START_SPLASH,
                            hostCapabilities.supportsHotStartSplashAd,
                            false,
                            visible = isFeatureVisible(context, ConfigManager.KEY_REMOVE_HOT_START_SPLASH),
                        )
                    )
                }
            }

            adBlockItems.addAll(listOf(
                SwitchItem(
                    UiText.Settings.BLOCK_IN_APP_DIALOG_LABEL,
                    UiText.Settings.BLOCK_IN_APP_DIALOG_DESC,
                    ConfigManager.KEY_BLOCK_IN_APP_DIALOG,
                    true,
                    false,
                    visible = isFeatureVisible(context, ConfigManager.KEY_BLOCK_IN_APP_DIALOG),
                ),
                SwitchItem(
                    UiText.Settings.BLOCK_UPDATE_DIALOG_LABEL,
                    UiText.Settings.BLOCK_UPDATE_DIALOG_DESC,
                    ConfigManager.KEY_BLOCK_UPDATE_DIALOG,
                    hostCapabilities.supportsUpdateDialogBlock,
                    false,
                    visible = isFeatureVisible(context, ConfigManager.KEY_BLOCK_UPDATE_DIALOG),
                ),
                SwitchItem(
                    UiText.Settings.BLOCK_FULL_SCREEN_BACKUP_LABEL,
                    UiText.Settings.BLOCK_FULL_SCREEN_BACKUP_DESC,
                    ConfigManager.KEY_BLOCK_FULL_SCREEN_BACKUP,
                    true,
                    false,
                    visible = isFeatureVisible(context, ConfigManager.KEY_BLOCK_FULL_SCREEN_BACKUP),
                ),
                SwitchItem(
                    UiText.Settings.BLOCK_APP_STORE_REVIEW_LABEL,
                    UiText.Settings.BLOCK_APP_STORE_REVIEW_DESC,
                    ConfigManager.KEY_BLOCK_APP_STORE_REVIEW,
                    true,
                    false,
                    visible = isFeatureVisible(context, ConfigManager.KEY_BLOCK_APP_STORE_REVIEW),
                ),
                SwitchItem(
                    UiText.Settings.BLOCK_SHARE_PUSH_GUIDE_LABEL,
                    UiText.Settings.BLOCK_SHARE_PUSH_GUIDE_DESC,
                    ConfigManager.KEY_BLOCK_SHARE_PUSH_GUIDE,
                    hostCapabilities.supportsSharePushGuideBlock,
                    false,
                    visible = isFeatureVisible(context, ConfigManager.KEY_BLOCK_SHARE_PUSH_GUIDE),
                )
            ))

            // 分组2：UI 净化
            val uiSimplifyItems = mutableListOf<SwitchItem>()

            // 仅解锁后显示高级功能
            if (ConfigManager.areRestrictedFeaturesUnlocked) {
                uiSimplifyItems.addAll(listOf(
                    SwitchItem(
                        UiText.Settings.HOME_CUSTOMIZE_LABEL,
                        UiText.Settings.HOME_CUSTOMIZE_DESC,
                        ConfigManager.KEY_HOME_CUSTOMIZE,
                        hostCapabilities.supportsHomeCustomize,
                        homeCustomizeDefault,
                        UiText.Settings.ACTION_ICON_SETTINGS,
                        onActionClick = {
                            showHomeCustomizeDialog(context, prefs)
                        },
                        visible = isFeatureVisible(context, ConfigManager.KEY_HOME_CUSTOMIZE),
                    ),
                    SwitchItem(
                        UiText.Settings.SHARE_PAGE_CUSTOMIZE_LABEL,
                        UiText.Settings.SHARE_PAGE_CUSTOMIZE_DESC,
                        ConfigManager.KEY_SHARE_PAGE_CUSTOMIZE,
                        true,
                        sharePageCustomizeDefault,
                        UiText.Settings.ACTION_ICON_SETTINGS,
                        onActionClick = {
                            showSharePageCustomizeDialog(context, prefs)
                        },
                        visible = isFeatureVisible(context, ConfigManager.KEY_SHARE_PAGE_CUSTOMIZE),
                    ),
                    SwitchItem(
                        UiText.Settings.MY_PAGE_CUSTOMIZE_LABEL,
                        UiText.Settings.MY_PAGE_CUSTOMIZE_DESC,
                        ConfigManager.KEY_MY_PAGE_CUSTOMIZE,
                        true,
                        myPageCustomizeDefault,
                        UiText.Settings.ACTION_ICON_SETTINGS,
                        onActionClick = {
                            showMyPageCustomizeDialog(context, prefs)
                        },
                        visible = isFeatureVisible(context, ConfigManager.KEY_MY_PAGE_CUSTOMIZE),
                    ),
                    SwitchItem(
                        UiText.Settings.MEMBER_CARD_CUSTOMIZE_LABEL,
                        UiText.Settings.MEMBER_CARD_CUSTOMIZE_DESC,
                        ConfigManager.KEY_MEMBER_CARD_CUSTOMIZE,
                        hostCapabilities.supportsMemberCardCustomize,
                        memberCardCustomizeDefault,
                        UiText.Settings.ACTION_ICON_SETTINGS,
                        onActionClick = {
                            showMemberCardCustomizeDialog(context, prefs)
                        },
                        visible = isFeatureVisible(context, ConfigManager.KEY_MEMBER_CARD_CUSTOMIZE),
                    ),
                    SwitchItem(
                        UiText.Settings.CUSTOM_BOTTOM_BAR_LABEL,
                        UiText.Settings.CUSTOM_BOTTOM_BAR_DESC,
                        ConfigManager.KEY_CUSTOM_BOTTOM_BAR,
                        true,
                        bottomBarCustomizeDefault,
                        UiText.Settings.ACTION_ICON_SETTINGS,
                        onActionClick = {
                            showBottomBarDialog(context, prefs)
                        },
                        visible = isFeatureVisible(context, ConfigManager.KEY_CUSTOM_BOTTOM_BAR),
                    ),
                ))
            }

            uiSimplifyItems.add(
                SwitchItem(
                    UiText.Settings.BLOCK_ALBUM_BACKUP_BAR_LABEL,
                    UiText.Settings.BLOCK_ALBUM_BACKUP_BAR_DESC,
                    ConfigManager.KEY_BLOCK_ALBUM_BACKUP_BAR,
                    true,
                    false,
                    visible = isFeatureVisible(context, ConfigManager.KEY_BLOCK_ALBUM_BACKUP_BAR),
                )
            )

            // 分组3：拓展功能
            val themeItems = mutableListOf(
                SwitchItem(
                    UiText.Settings.FOLLOW_SYSTEM_NIGHT_MODE_LABEL,
                    UiText.Settings.FOLLOW_SYSTEM_NIGHT_MODE_DESC,
                    ConfigManager.KEY_FOLLOW_SYSTEM_NIGHT_MODE,
                    true,
                    false,
                    visible = isFeatureVisible(context, ConfigManager.KEY_FOLLOW_SYSTEM_NIGHT_MODE),
                ),
            )

            // 仅解锁后显示性能优化
            if (ConfigManager.areRestrictedFeaturesUnlocked) {
                themeItems.add(
                    SwitchItem(
                        UiText.Settings.PERFORMANCE_OPTIMIZE_LABEL,
                        UiText.Settings.PERFORMANCE_OPTIMIZE_DESC,
                        ConfigManager.KEY_PERFORMANCE_OPTIMIZE,
                        true,
                        performanceOptimizeDefault,
                        UiText.Settings.ACTION_ICON_SETTINGS,
                        onActionClick = {
                            showPerformanceOptimizeDialog(context, prefs)
                        },
                        visible = isFeatureVisible(context, ConfigManager.KEY_PERFORMANCE_OPTIMIZE),
                    )
                )
            }

            val debugItems = listOf(
                SwitchItem(
                    UiText.Settings.EXPERIMENTAL_DEXKIT_LABEL,
                    UiText.Settings.EXPERIMENTAL_DEXKIT_DESC,
                    ConfigManager.KEY_ENABLE_EXPERIMENTAL_DEXKIT,
                    true,
                    false,
                    visible = hostCapabilities.supportsStandaloneHotStartSplashRemove,
                ),
                SwitchItem(
                    UiText.Settings.DETAILED_LOGGING_LABEL,
                    UiText.Settings.DETAILED_LOGGING_DESC,
                    ConfigManager.KEY_ENABLE_DETAILED_LOGGING,
                    true,
                    false,
                ),
                SwitchItem(
                    UiText.Settings.CLEAR_LOGS_LABEL,
                    UiText.Settings.CLEAR_LOGS_DESC,
                    null,
                    true,
                    false,
                    UiText.Settings.ACTION_ICON_CLEAR,
                    showSwitch = false,
                    onActionClick = {
                        SettingsDebugActions.showClearLogsConfirmDialog(context)
                    },
                ),
                SwitchItem(
                    UiText.Settings.RESET_MODULE_SETTINGS_LABEL,
                    UiText.Settings.RESET_MODULE_SETTINGS_DESC,
                    null,
                    true,
                    false,
                    UiText.Settings.ACTION_ICON_RESET,
                    showSwitch = false,
                    onActionClick = {
                        SettingsDebugActions.showResetModuleSettingsConfirmDialog(context) {
                            restartHostApp(context)
                        }
                    },
                ),
            )

            val groups = mutableListOf(
                SettingGroup(UiText.Settings.GROUP_CONTENT_BLOCK, adBlockItems),
                SettingGroup(UiText.Settings.GROUP_UI_OPTIMIZE, uiSimplifyItems),
                SettingGroup(UiText.Settings.GROUP_THEME, themeItems),
                SettingGroup(UiText.Settings.GROUP_DEBUG, debugItems),
            )

            val runtimeSupportByKey = mutableMapOf<String?, SwitchRuntimeSupport>()
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
                        (12 * density).toInt()
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

                    val rowView = createSwitchRow(
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
                    )
                    root.addView(rowView)
                }
                hasRenderedGroup = true
            }

            // About section
            root.addView(createDivider(context, padding))
            val tokensForDefault = UiStyle.tokens(context)
            val aboutContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, padding)

                val gap = View(context)
                gap.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (12 * density).toInt()
                )
                addView(gap)

                addView(TextView(context).apply {
                    text = UiText.Settings.ABOUT
                    textSize = 12.5f
                    letterSpacing = 0.04f
                    setTextColor(tokensForDefault.accent)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setPadding(0, (padding * 0.7f).toInt(), 0, (padding * 0.35f).toInt())
                })
            }

            val versionInfo = buildVersionDisplayInfo(context)
            val aboutItemsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            aboutContainer.addView(aboutItemsContainer)

            root.addView(aboutContainer)

            val scrollContainer = ScrollView(context).apply {
                addView(
                    root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
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

            val dialogTheme = if (tokensTitle.night) {
                android.R.style.Theme_DeviceDefault_Dialog_Alert
            } else {
                android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            }
            val builder = AlertDialog.Builder(context, dialogTheme)
            builder.setCustomTitle(titleView)
            builder.setView(scrollContainer)
            builder.setPositiveButton(UiText.Settings.SAVE_AND_RESTART) { _, _ ->
                Toast.makeText(
                    context,
                    UiText.Settings.SETTINGS_SAVED_RESTARTING,
                    Toast.LENGTH_SHORT
                ).show()
                restartHostApp(context)
            }

            val dialog = builder.create()

            // 在 show 之前设置 versionClickListener，以便它可以访问 dialog
            val versionClickListener = if (!ConfigManager.areRestrictedFeaturesUnlocked) {
                View.OnClickListener {
                    versionTapCount++
                    if (versionTapCount >= RESTRICTED_FEATURE_UNLOCK_TAP_COUNT) {
                        versionTapCount = 0
                        showRestrictedFeatureWarningDialog(context) {
                            ConfigManager.setRestrictedFeaturesUnlocked(context, true)
                            dialog.dismiss()
                            Handler(Looper.getMainLooper()).postDelayed({
                                showModuleSettingsDialog(context, classLoader)
                            }, 100)
                            Toast.makeText(
                                context,
                                UiText.Settings.RESTRICTED_FEATURE_UNLOCKED_TEXT,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } else null

            // 重新渲染带有正确 versionClickListener 的 aboutItems
            aboutItemsContainer.removeAllViews()
            val finalAboutItems = listOf(
                AboutInfoManager.AboutItem(
                    UiText.Settings.VERSION,
                    UiText.Settings.aboutVersionSummary(
                        hostBuildType = versionInfo.hostBuildType,
                        hostVersion = versionInfo.hostVersion,
                        moduleBuildType = versionInfo.moduleBuildType,
                        moduleVersion = versionInfo.moduleVersion,
                    ),
                    null,
                    versionClickListener,
                ),
                AboutInfoManager.AboutItem(
                    UiText.Settings.AUTHOR,
                    UiText.Settings.AUTHOR_NAME,
                    "https://github.com/xiyunmn/PureDuPan"
                ),
            ) + AboutInfoManager.loadCachedItemsForSettings()
            for (aboutItem in finalAboutItems) {
                aboutItemsContainer.addView(
                    createAboutItem(
                        context = context,
                        density = density,
                        padding = padding,
                        title = aboutItem.title,
                        content = aboutItem.description,
                        url = aboutItem.url,
                        onClick = aboutItem.onClickListener?.let { listener ->
                            { listener.onClick(null) }
                        },
                    )
                )
            }

            dialog.show()
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
                            showModuleSettingsDialog(context, classLoader, scrollY)
                        }
                        XposedCompat.logD("[SettingsMenuHook] settings dialog recreated after host theme change: $reason")
                    } catch (t: Throwable) {
                        XposedCompat.logW("[SettingsMenuHook] recreate after theme change failed: ${t.message}")
                    }
                }
            }
            dialog.setOnDismissListener {
                unregisterThemeListener?.invoke()
                unregisterThemeListener = null
            }
            dialog.window?.let { window ->
                val windowDensity = context.resources.displayMetrics.density
                applyUnifiedDialogCardStyle(window, windowDensity)
                UiStyle.animateDialogEntry(window.decorView, windowDensity)
            }
        } catch (t: Throwable) {
            XposedCompat.log("[SettingsMenuHook] FAILED to show settings dialog: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun showSharePageCustomizeDialog(context: Context, prefs: android.content.SharedPreferences) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val fabRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.REMOVE_HOME_FAB_LABEL,
                UiText.Settings.REMOVE_HOME_FAB_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_REMOVE_HOME_FAB, false),
            )
            addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = createCustomHideWidgetSectionTitle(context, padding),
                rows = visibleRows(
                    context,
                    ConfigManager.KEY_REMOVE_HOME_FAB to fabRow,
                ),
            )

            val fabSwitch = findSwitchView(fabRow)
            if (fabSwitch == null) {
                XposedCompat.logW("[SettingsMenuHook] showSharePageCustomizeDialog failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setTitle(UiText.Settings.SHARE_PAGE_CUSTOMIZE_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    prefs.edit()
                        .putBoolean(ConfigManager.KEY_SHARE_PAGE_CUSTOMIZE, fabSwitch.isChecked)
                        .putBoolean(ConfigManager.KEY_REMOVE_HOME_FAB, fabSwitch.isChecked)
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.SHARE_PAGE_CUSTOMIZE_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            showStableSubDialog(dialog, density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showSharePageCustomizeDialog failed: ${t.message}")
        }
    }

    private fun showBottomBarDialog(context: Context, prefs: android.content.SharedPreferences) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()
            val tokens = UiStyle.tokens(context)

            val initialSelection = ConfigManager.BottomBarTabSelection(
                hideFile = prefs.getBoolean(ConfigManager.KEY_HIDE_TAB_FILE, false),
                hideShare = prefs.getBoolean(ConfigManager.KEY_HIDE_TAB_SHARE, false),
                hideVip = prefs.getBoolean(ConfigManager.KEY_HIDE_TAB_VIP, false),
                hideAigc = prefs.getBoolean(ConfigManager.KEY_HIDE_TAB_AIGC, false),
                hideHome = prefs.getBoolean(ConfigManager.KEY_HIDE_TAB_HOME, false),
                hideMine = prefs.getBoolean(ConfigManager.KEY_HIDE_TAB_MINE, false),
            )

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            // 底栏 AI 替换为会员
            val replaceAiRow = createSwitchRow(
                context, prefs, UiText.Settings.REPLACE_BOTTOM_AI_LABEL,
                UiText.Settings.REPLACE_BOTTOM_AI_DESC, null, padding, true,
                prefs.getBoolean(ConfigManager.KEY_REPLACE_BOTTOM_AI, false),
            )
            val replaceAiSwitch = findSwitchView(replaceAiRow)
            val showReplaceAi = isFeatureVisible(context, ConfigManager.KEY_REPLACE_BOTTOM_AI)
            if (showReplaceAi) {
                root.addView(replaceAiRow)
            }
            val badgeRow = createSwitchRow(
                context, prefs, UiText.Settings.BLOCK_BOTTOM_BADGE_LABEL,
                UiText.Settings.BLOCK_BOTTOM_BADGE_DESC, null, padding, true,
                prefs.getBoolean(ConfigManager.KEY_BLOCK_BOTTOM_BADGE, false),
            )
            val showBottomBadge = isFeatureVisible(context, ConfigManager.KEY_BLOCK_BOTTOM_BADGE)
            if (showBottomBadge) {
                root.addView(badgeRow)
            }

            val homeRow = createSwitchRow(
                context, prefs, UiText.Settings.BOTTOM_BAR_HIDE_TAB_HOME_LABEL,
                null, null, padding, true, initialSelection.hideHome,
            )
            val fileRow = createSwitchRow(
                context, prefs, UiText.Settings.BOTTOM_BAR_HIDE_TAB_FILE_LABEL,
                null, null, padding, true, initialSelection.hideFile,
            )
            val shareRow = createSwitchRow(
                context, prefs, UiText.Settings.BOTTOM_BAR_HIDE_TAB_SHARE_LABEL,
                null, null, padding, true, initialSelection.hideShare,
            )
            val vipRow = createSwitchRow(
                context, prefs, UiText.Settings.BOTTOM_BAR_HIDE_TAB_VIP_LABEL,
                null, null, padding, true, initialSelection.hideVip,
            )
            val aigcRow = createSwitchRow(
                context, prefs, UiText.Settings.BOTTOM_BAR_HIDE_TAB_AIGC_LABEL,
                null, null, padding, true, initialSelection.hideAigc,
            )
            val mineRow = createSwitchRow(
                context, prefs, UiText.Settings.BOTTOM_BAR_HIDE_TAB_MINE_LABEL,
                null, null, padding, true, initialSelection.hideMine,
            )

            val showHideHome = isFeatureVisible(context, ConfigManager.KEY_HIDE_TAB_HOME)
            val showHideFile = isFeatureVisible(context, ConfigManager.KEY_HIDE_TAB_FILE)
            val showHideShare = isFeatureVisible(context, ConfigManager.KEY_HIDE_TAB_SHARE)
            val showHideVip = isFeatureVisible(context, ConfigManager.KEY_HIDE_TAB_VIP)
            val showHideAigc = isFeatureVisible(context, ConfigManager.KEY_HIDE_TAB_AIGC)
            val showHideMine = isFeatureVisible(context, ConfigManager.KEY_HIDE_TAB_MINE)
            addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = TextView(context).apply {
                    text = UiText.Settings.BOTTOM_BAR_TAB_SECTION_TITLE
                    textSize = 12.5f
                    letterSpacing = 0.04f
                    setTextColor(tokens.accent)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setPadding(0, (padding * 0.45f).toInt(), 0, (padding * 0.2f).toInt())
                },
                rows = visibleRows(
                    context,
                    ConfigManager.KEY_HIDE_TAB_HOME to homeRow,
                    ConfigManager.KEY_HIDE_TAB_FILE to fileRow,
                    ConfigManager.KEY_HIDE_TAB_AIGC to aigcRow,
                    ConfigManager.KEY_HIDE_TAB_SHARE to shareRow,
                    ConfigManager.KEY_HIDE_TAB_VIP to vipRow,
                    ConfigManager.KEY_HIDE_TAB_MINE to mineRow,
                ),
                addDividerBefore = root.childCount > 0,
            )

            val homeSwitch = findSwitchView(homeRow)
            val fileSwitch = findSwitchView(fileRow)
            val shareSwitch = findSwitchView(shareRow)
            val vipSwitch = findSwitchView(vipRow)
            val aigcSwitch = findSwitchView(aigcRow)
            val mineSwitch = findSwitchView(mineRow)
            val badgeSwitch = findSwitchView(badgeRow)
            if (replaceAiSwitch == null || badgeSwitch == null ||
                homeSwitch == null || fileSwitch == null || shareSwitch == null ||
                vipSwitch == null || aigcSwitch == null || mineSwitch == null
            ) {
                XposedCompat.logW("[SettingsMenuHook] showBottomBarDialog failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setTitle(UiText.Settings.BOTTOM_BAR_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val rawSelection = ConfigManager.BottomBarTabSelection(
                        hideHome = showHideHome && homeSwitch.isChecked,
                        hideFile = showHideFile && fileSwitch.isChecked,
                        hideShare = showHideShare && shareSwitch.isChecked,
                        hideVip = showHideVip && vipSwitch.isChecked,
                        hideAigc = showHideAigc && aigcSwitch.isChecked,
                        hideMine = showHideMine && mineSwitch.isChecked,
                    )
                    if (!rawSelection.hasVisibleTab()) {
                        Toast.makeText(
                            context,
                            UiText.Settings.BOTTOM_BAR_AT_LEAST_ONE,
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                    val normalized = ConfigManager.normalizeBottomBarSelection(rawSelection)
                    val hasEnabledBottomBarOption =
                        (showReplaceAi && replaceAiSwitch.isChecked) ||
                            (showBottomBadge && badgeSwitch.isChecked) ||
                            normalized.hideHome ||
                            normalized.hideFile ||
                            normalized.hideShare ||
                            normalized.hideVip ||
                            normalized.hideAigc ||
                            normalized.hideMine
                    prefs.edit()
                        .putBoolean(ConfigManager.KEY_CUSTOM_BOTTOM_BAR, hasEnabledBottomBarOption)
                        .putBoolean(ConfigManager.KEY_REPLACE_BOTTOM_AI, replaceAiSwitch.isChecked)
                        .putBoolean(ConfigManager.KEY_BLOCK_BOTTOM_BADGE, badgeSwitch.isChecked)
                        .putBoolean(ConfigManager.KEY_HIDE_TAB_HOME, normalized.hideHome)
                        .putBoolean(ConfigManager.KEY_HIDE_TAB_FILE, normalized.hideFile)
                        .putBoolean(ConfigManager.KEY_HIDE_TAB_SHARE, normalized.hideShare)
                        .putBoolean(ConfigManager.KEY_HIDE_TAB_VIP, normalized.hideVip)
                        .putBoolean(ConfigManager.KEY_HIDE_TAB_AIGC, normalized.hideAigc)
                        .putBoolean(ConfigManager.KEY_HIDE_TAB_MINE, normalized.hideMine)
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.BOTTOM_BAR_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            showStableSubDialog(dialog, density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showBottomBarDialog failed: ${t.message}")
        }
    }

    private fun showPerformanceOptimizeDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val garbageCleanRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER_LABEL,
                UiText.Settings.DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER_DESC,
                null,
                padding,
                hostCapabilities(context).supportsGarbageCleanServiceOptimize,
                prefs.getBoolean(ConfigManager.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER, false),
            )
            val datapackSocketRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.DISABLE_DATAPACK_SOCKET_REGISTER_LABEL,
                UiText.Settings.DISABLE_DATAPACK_SOCKET_REGISTER_DESC,
                null,
                padding,
                hostCapabilities(context).supportsDatapackSocketOptimize,
                prefs.getBoolean(ConfigManager.KEY_DISABLE_DATAPACK_SOCKET_REGISTER, false),
            )
            val aigcBackgroundComponentRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.DISABLE_AIGC_BACKGROUND_COMPONENT_LABEL,
                UiText.Settings.DISABLE_AIGC_BACKGROUND_COMPONENT_DESC,
                null,
                padding,
                hostCapabilities(context).supportsAigcBackgroundOptimize,
                prefs.getBoolean(ConfigManager.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT, false),
            )
            val dynamicPluginAutoDownloadRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD_LABEL,
                UiText.Settings.DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD_DESC,
                null,
                padding,
                hostCapabilities(context).supportsDynamicPluginAutoDownloadBlock,
                prefs.getBoolean(ConfigManager.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD, false),
            )
            val oemPushServiceRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.DISABLE_OEM_PUSH_SERVICE_LABEL,
                UiText.Settings.DISABLE_OEM_PUSH_SERVICE_DESC,
                null,
                padding,
                hostCapabilities(context).supportsOemPushHook,
                prefs.getBoolean(ConfigManager.KEY_DISABLE_OEM_PUSH_SERVICE, false),
            )
            val videoAdPreloadRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.DISABLE_VIDEO_AD_PRELOAD_LABEL,
                UiText.Settings.DISABLE_VIDEO_AD_PRELOAD_DESC,
                null,
                padding,
                hostCapabilities(context).supportsVideoAdPreloadBlock,
                prefs.getBoolean(ConfigManager.KEY_DISABLE_VIDEO_AD_PRELOAD, false),
            )
            val adSdkInitRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.DISABLE_AD_SDK_INIT_LABEL,
                UiText.Settings.DISABLE_AD_SDK_INIT_DESC,
                null,
                padding,
                hostCapabilities(context).supportsAdSdkInitBlock,
                prefs.getBoolean(ConfigManager.KEY_DISABLE_AD_SDK_INIT, false),
            )
            val swanPreloadRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.DISABLE_SWAN_PRELOAD_LABEL,
                UiText.Settings.DISABLE_SWAN_PRELOAD_DESC,
                null,
                padding,
                hostCapabilities(context).supportsSwanPreloadBlock,
                prefs.getBoolean(ConfigManager.KEY_DISABLE_SWAN_PRELOAD, false),
            )
            val thumbnailOperatorServiceRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.DISABLE_THUMBNAIL_OPERATOR_SERVICE_LABEL,
                UiText.Settings.DISABLE_THUMBNAIL_OPERATOR_SERVICE_DESC,
                null,
                padding,
                hostCapabilities(context).supportsThumbnailOperatorServiceBlock,
                prefs.getBoolean(ConfigManager.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE, false),
            )
            val incentiveBusinessServiceRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.DISABLE_INCENTIVE_BUSINESS_SERVICE_LABEL,
                UiText.Settings.DISABLE_INCENTIVE_BUSINESS_SERVICE_DESC,
                null,
                padding,
                hostCapabilities(context).supportsIncentiveBusinessServiceBlock,
                prefs.getBoolean(ConfigManager.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE, false),
            )
            val mediaBrowserServiceAutostartRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART_LABEL,
                UiText.Settings.DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART_DESC,
                null,
                padding,
                hostCapabilities(context).supportsAudioCircleAutostartBlock,
                prefs.getBoolean(ConfigManager.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART, false),
            )
            val iconResourceDownloadRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.DISABLE_ICON_RESOURCE_DOWNLOAD_LABEL,
                UiText.Settings.DISABLE_ICON_RESOURCE_DOWNLOAD_DESC,
                null,
                padding,
                hostCapabilities(context).supportsIconResourceDownloadBlock,
                prefs.getBoolean(ConfigManager.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD, false),
            )
            val b2fGuidancePrefetchRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.DISABLE_B2F_GUIDANCE_PREFETCH_LABEL,
                UiText.Settings.DISABLE_B2F_GUIDANCE_PREFETCH_DESC,
                null,
                padding,
                hostCapabilities(context).supportsB2fGuidancePrefetchBlock,
                prefs.getBoolean(ConfigManager.KEY_DISABLE_B2F_GUIDANCE_PREFETCH, false),
            )
            addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = createPerformanceSectionTitle(
                    context,
                    padding,
                    UiText.Settings.PERFORMANCE_GROUP_POST_INIT,
                ),
                rows = visibleRows(
                    context,
                    ConfigManager.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER to garbageCleanRow,
                    ConfigManager.KEY_DISABLE_DATAPACK_SOCKET_REGISTER to datapackSocketRow,
                    ConfigManager.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT to aigcBackgroundComponentRow,
                ),
            )

            addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = createPerformanceSectionTitle(
                    context,
                    padding,
                    UiText.Settings.PERFORMANCE_GROUP_STARTUP_PREFETCH,
                ),
                rows = visibleRows(
                    context,
                    ConfigManager.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD to dynamicPluginAutoDownloadRow,
                    ConfigManager.KEY_DISABLE_SWAN_PRELOAD to swanPreloadRow,
                    ConfigManager.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD to iconResourceDownloadRow,
                    ConfigManager.KEY_DISABLE_B2F_GUIDANCE_PREFETCH to b2fGuidancePrefetchRow,
                ),
            )

            addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = createPerformanceSectionTitle(
                    context,
                    padding,
                    UiText.Settings.PERFORMANCE_GROUP_RUNTIME_SERVICE,
                ),
                rows = visibleRows(
                    context,
                    ConfigManager.KEY_DISABLE_OEM_PUSH_SERVICE to oemPushServiceRow,
                    ConfigManager.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE to thumbnailOperatorServiceRow,
                    ConfigManager.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART to mediaBrowserServiceAutostartRow,
                ),
            )

            addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = createPerformanceSectionTitle(
                    context,
                    padding,
                    UiText.Settings.PERFORMANCE_GROUP_AD_INCENTIVE,
                ),
                rows = visibleRows(
                    context,
                    ConfigManager.KEY_DISABLE_AD_SDK_INIT to adSdkInitRow,
                    ConfigManager.KEY_DISABLE_VIDEO_AD_PRELOAD to videoAdPreloadRow,
                    ConfigManager.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE to incentiveBusinessServiceRow,
                ),
            )

            val garbageCleanSwitch = findSwitchView(garbageCleanRow)
            val datapackSocketSwitch = findSwitchView(datapackSocketRow)
            val aigcBackgroundComponentSwitch = findSwitchView(aigcBackgroundComponentRow)
            val dynamicPluginAutoDownloadSwitch = findSwitchView(dynamicPluginAutoDownloadRow)
            val oemPushServiceSwitch = findSwitchView(oemPushServiceRow)
            val videoAdPreloadSwitch = findSwitchView(videoAdPreloadRow)
            val adSdkInitSwitch = findSwitchView(adSdkInitRow)
            val swanPreloadSwitch = findSwitchView(swanPreloadRow)
            val thumbnailOperatorServiceSwitch = findSwitchView(thumbnailOperatorServiceRow)
            val incentiveBusinessServiceSwitch = findSwitchView(incentiveBusinessServiceRow)
            val mediaBrowserServiceAutostartSwitch = findSwitchView(mediaBrowserServiceAutostartRow)
            val iconResourceDownloadSwitch = findSwitchView(iconResourceDownloadRow)
            val b2fGuidancePrefetchSwitch = findSwitchView(b2fGuidancePrefetchRow)
            if (
                garbageCleanSwitch == null ||
                datapackSocketSwitch == null ||
                aigcBackgroundComponentSwitch == null ||
                dynamicPluginAutoDownloadSwitch == null ||
                oemPushServiceSwitch == null ||
                videoAdPreloadSwitch == null ||
                adSdkInitSwitch == null ||
                swanPreloadSwitch == null ||
                thumbnailOperatorServiceSwitch == null ||
                incentiveBusinessServiceSwitch == null ||
                mediaBrowserServiceAutostartSwitch == null ||
                iconResourceDownloadSwitch == null ||
                b2fGuidancePrefetchSwitch == null
            ) {
                XposedCompat.logW("[SettingsMenuHook] showPerformanceOptimizeDialog failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setTitle(UiText.Settings.PERFORMANCE_OPTIMIZE_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val hasEnabledPerformanceOption =
                        garbageCleanSwitch.isChecked ||
                            datapackSocketSwitch.isChecked ||
                            aigcBackgroundComponentSwitch.isChecked ||
                            dynamicPluginAutoDownloadSwitch.isChecked ||
                            oemPushServiceSwitch.isChecked ||
                            videoAdPreloadSwitch.isChecked ||
                            adSdkInitSwitch.isChecked ||
                            swanPreloadSwitch.isChecked ||
                            thumbnailOperatorServiceSwitch.isChecked ||
                            incentiveBusinessServiceSwitch.isChecked ||
                            mediaBrowserServiceAutostartSwitch.isChecked ||
                            iconResourceDownloadSwitch.isChecked ||
                            b2fGuidancePrefetchSwitch.isChecked
                    prefs.edit()
                        .putBoolean(ConfigManager.KEY_PERFORMANCE_OPTIMIZE, hasEnabledPerformanceOption)
                        .putBoolean(
                            ConfigManager.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER,
                            garbageCleanSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_DISABLE_DATAPACK_SOCKET_REGISTER,
                            datapackSocketSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT,
                            aigcBackgroundComponentSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD,
                            dynamicPluginAutoDownloadSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_DISABLE_OEM_PUSH_SERVICE,
                            oemPushServiceSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_DISABLE_VIDEO_AD_PRELOAD,
                            videoAdPreloadSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_DISABLE_AD_SDK_INIT,
                            adSdkInitSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_DISABLE_SWAN_PRELOAD,
                            swanPreloadSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE,
                            thumbnailOperatorServiceSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE,
                            incentiveBusinessServiceSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART,
                            mediaBrowserServiceAutostartSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD,
                            iconResourceDownloadSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_DISABLE_B2F_GUIDANCE_PREFETCH,
                            b2fGuidancePrefetchSwitch.isChecked,
                        )
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.PERFORMANCE_OPTIMIZE_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            showStableSubDialog(dialog, density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showPerformanceOptimizeDialog failed: ${t.message}")
        }
    }

    private fun showHomeCustomizeDialog(context: Context, prefs: android.content.SharedPreferences) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val topPromotionRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_HOME_TOP_PROMOTION_LABEL,
                UiText.Settings.HIDE_HOME_TOP_PROMOTION_DESC,
                null,
                padding,
                true,
                ConfigManager.readHomeTopPromotionHidden(prefs),
            )
            addVisibleRows(
                root,
                visibleRows(
                    context,
                    ConfigManager.KEY_HIDE_HOME_TOP_PROMOTION to topPromotionRow,
                ),
            )

            val placeholderRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_HOME_SEARCH_PLACEHOLDER_LABEL,
                UiText.Settings.HIDE_HOME_SEARCH_PLACEHOLDER_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_SEARCH_PLACEHOLDER, false),
            )
            val aigcIconRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_HOME_SEARCH_AIGC_ICON_LABEL,
                UiText.Settings.HIDE_HOME_SEARCH_AIGC_ICON_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_SEARCH_AIGC_ICON, false),
            )
            val feedTipRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_HOME_FEED_TIP_LABEL,
                UiText.Settings.HIDE_HOME_FEED_TIP_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_FEED_TIP, false),
            )
            val bannerRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_HOME_BANNER_LABEL,
                UiText.Settings.HIDE_HOME_BANNER_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_BANNER, false),
            )

            addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = createCustomHideWidgetSectionTitle(context, padding),
                rows = visibleRows(
                    context,
                    ConfigManager.KEY_HIDE_HOME_SEARCH_PLACEHOLDER to placeholderRow,
                    ConfigManager.KEY_HIDE_HOME_SEARCH_AIGC_ICON to aigcIconRow,
                    ConfigManager.KEY_HIDE_HOME_FEED_TIP to feedTipRow,
                    ConfigManager.KEY_HIDE_HOME_BANNER to bannerRow,
                ),
                addDividerBefore = root.childCount > 0,
            )

            val memoriesSectionRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_HOME_MEMORIES_SECTION_LABEL,
                UiText.Settings.HIDE_HOME_MEMORIES_SECTION_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_MEMORIES_SECTION, false),
            )
            val saveSectionRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_HOME_SAVE_SECTION_LABEL,
                UiText.Settings.HIDE_HOME_SAVE_SECTION_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_SAVE_SECTION, false),
            )
            val recentSectionRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_HOME_RECENT_SECTION_LABEL,
                UiText.Settings.HIDE_HOME_RECENT_SECTION_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_RECENT_SECTION, false),
            )

            addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = createCustomHideSectionSectionTitle(context, padding),
                rows = visibleRows(
                    context,
                    ConfigManager.KEY_HIDE_HOME_MEMORIES_SECTION to memoriesSectionRow,
                    ConfigManager.KEY_HIDE_HOME_SAVE_SECTION to saveSectionRow,
                    ConfigManager.KEY_HIDE_HOME_RECENT_SECTION to recentSectionRow,
                ),
                addDividerBefore = root.childCount > 0,
            )

            val topPromotionSwitch = findSwitchView(topPromotionRow)
            val placeholderSwitch = findSwitchView(placeholderRow)
            val aigcIconSwitch = findSwitchView(aigcIconRow)
            val feedTipSwitch = findSwitchView(feedTipRow)
            val bannerSwitch = findSwitchView(bannerRow)
            val memoriesSectionSwitch = findSwitchView(memoriesSectionRow)
            val saveSectionSwitch = findSwitchView(saveSectionRow)
            val recentSectionSwitch = findSwitchView(recentSectionRow)
            if (
                topPromotionSwitch == null ||
                placeholderSwitch == null ||
                aigcIconSwitch == null ||
                feedTipSwitch == null ||
                bannerSwitch == null ||
                memoriesSectionSwitch == null ||
                saveSectionSwitch == null ||
                recentSectionSwitch == null
            ) {
                XposedCompat.logW("[SettingsMenuHook] showHomeCustomizeDialog failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setTitle(UiText.Settings.HOME_CUSTOMIZE_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val hasEnabledHomeOption =
                        isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_TOP_PROMOTION) &&
                            topPromotionSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_SEARCH_PLACEHOLDER) &&
                            placeholderSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_SEARCH_AIGC_ICON) &&
                            aigcIconSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_FEED_TIP) &&
                            feedTipSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_BANNER) &&
                            bannerSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_MEMORIES_SECTION) &&
                            memoriesSectionSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_SAVE_SECTION) &&
                            saveSectionSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_RECENT_SECTION) &&
                            recentSectionSwitch.isChecked
                    prefs.edit()
                        .putBoolean(ConfigManager.KEY_HOME_CUSTOMIZE, hasEnabledHomeOption)
                        .putBoolean(ConfigManager.KEY_HIDE_HOME_TOP_PROMOTION, topPromotionSwitch.isChecked)
                        .putBoolean(
                            ConfigManager.KEY_HIDE_HOME_SEARCH_PLACEHOLDER,
                            placeholderSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_HIDE_HOME_SEARCH_AIGC_ICON,
                            aigcIconSwitch.isChecked,
                        )
                        .putBoolean(ConfigManager.KEY_HIDE_HOME_FEED_TIP, feedTipSwitch.isChecked)
                        .putBoolean(ConfigManager.KEY_HIDE_HOME_BANNER, bannerSwitch.isChecked)
                        .putBoolean(
                            ConfigManager.KEY_HIDE_HOME_MEMORIES_SECTION,
                            memoriesSectionSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_HIDE_HOME_SAVE_SECTION,
                            saveSectionSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_HIDE_HOME_RECENT_SECTION,
                            recentSectionSwitch.isChecked,
                        )
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.HOME_CUSTOMIZE_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            showStableSubDialog(dialog, density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showHomeCustomizeDialog failed: ${t.message}")
        }
    }

    private fun showMyPageCustomizeDialog(context: Context, prefs: android.content.SharedPreferences) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val renewRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_RENEW_BUTTON_LABEL,
                UiText.Settings.HIDE_RENEW_BUTTON_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_RENEW_BUTTON, false),
            )
            val gameCenterRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.REMOVE_GAME_CENTER_LABEL,
                UiText.Settings.REMOVE_GAME_CENTER_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_REMOVE_GAME_CENTER, false),
            )
            val bannerRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.REMOVE_ABOUT_ME_BANNER_LABEL,
                UiText.Settings.REMOVE_ABOUT_ME_BANNER_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_REMOVE_ABOUT_ME_BANNER, false),
            )
            val serviceRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.REMOVE_MY_SERVICE_LABEL,
                UiText.Settings.REMOVE_MY_SERVICE_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_REMOVE_MY_SERVICE, false),
            )
            val coinCenterBubbleRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_ABOUT_ME_COIN_CENTER_BUBBLE_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_COIN_CENTER_BUBBLE_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE, false),
            )
            val signInDotRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_ABOUT_ME_SIGN_IN_DOT_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_SIGN_IN_DOT_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_SIGN_IN_DOT, false),
            )
            val aiCoinAssetRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_ABOUT_ME_AI_COIN_ASSET_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_AI_COIN_ASSET_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_AI_COIN_ASSET, false),
            )
            val manageSpaceTextRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_ABOUT_ME_MANAGE_SPACE_TEXT_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_MANAGE_SPACE_TEXT_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT, false),
            )
            val rewardTextRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_ABOUT_ME_REWARD_TEXT_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_REWARD_TEXT_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_REWARD_TEXT, false),
            )
            val accountExitTextRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT, false),
            )
            val starSkinTextRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_ABOUT_ME_STAR_SKIN_TEXT_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_STAR_SKIN_TEXT_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT, false),
            )
            val freeDataCardTextRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT, false),
            )

            addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = createCustomHideWidgetSectionTitle(context, padding),
                rows = visibleRows(
                    context,
                    ConfigManager.KEY_HIDE_RENEW_BUTTON to renewRow,
                    ConfigManager.KEY_REMOVE_GAME_CENTER to gameCenterRow,
                    ConfigManager.KEY_REMOVE_ABOUT_ME_BANNER to bannerRow,
                    ConfigManager.KEY_REMOVE_MY_SERVICE to serviceRow,
                    ConfigManager.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE to coinCenterBubbleRow,
                    ConfigManager.KEY_HIDE_ABOUT_ME_SIGN_IN_DOT to signInDotRow,
                    ConfigManager.KEY_HIDE_ABOUT_ME_AI_COIN_ASSET to aiCoinAssetRow,
                ),
            )
            addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = createCustomHideTextWidgetSectionTitle(context, padding),
                rows = visibleRows(
                    context,
                    ConfigManager.KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT to manageSpaceTextRow,
                    ConfigManager.KEY_HIDE_ABOUT_ME_REWARD_TEXT to rewardTextRow,
                    ConfigManager.KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT to accountExitTextRow,
                    ConfigManager.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT to starSkinTextRow,
                    ConfigManager.KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT to freeDataCardTextRow,
                ),
                addDividerBefore = root.childCount > 0,
            )

            val renewSwitch = findSwitchView(renewRow)
            val gameCenterSwitch = findSwitchView(gameCenterRow)
            val bannerSwitch = findSwitchView(bannerRow)
            val serviceSwitch = findSwitchView(serviceRow)
            val coinCenterBubbleSwitch = findSwitchView(coinCenterBubbleRow)
            val signInDotSwitch = findSwitchView(signInDotRow)
            val aiCoinAssetSwitch = findSwitchView(aiCoinAssetRow)
            val manageSpaceTextSwitch = findSwitchView(manageSpaceTextRow)
            val rewardTextSwitch = findSwitchView(rewardTextRow)
            val accountExitTextSwitch = findSwitchView(accountExitTextRow)
            val starSkinTextSwitch = findSwitchView(starSkinTextRow)
            val freeDataCardTextSwitch = findSwitchView(freeDataCardTextRow)
            if (
                renewSwitch == null ||
                gameCenterSwitch == null ||
                bannerSwitch == null ||
                serviceSwitch == null ||
                coinCenterBubbleSwitch == null ||
                signInDotSwitch == null ||
                aiCoinAssetSwitch == null ||
                manageSpaceTextSwitch == null ||
                rewardTextSwitch == null ||
                accountExitTextSwitch == null ||
                starSkinTextSwitch == null ||
                freeDataCardTextSwitch == null
            ) {
                XposedCompat.logW("[SettingsMenuHook] showMyPageCustomizeDialog failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setTitle(UiText.Settings.MY_PAGE_CUSTOMIZE_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val hasEnabledMyPageOption =
                        isFeatureVisible(context, ConfigManager.KEY_HIDE_RENEW_BUTTON) &&
                            renewSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_REMOVE_GAME_CENTER) &&
                            gameCenterSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_REMOVE_ABOUT_ME_BANNER) &&
                            bannerSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_REMOVE_MY_SERVICE) &&
                            serviceSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE) &&
                            coinCenterBubbleSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_SIGN_IN_DOT) &&
                            signInDotSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_AI_COIN_ASSET) &&
                            aiCoinAssetSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT) &&
                            manageSpaceTextSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_REWARD_TEXT) &&
                            rewardTextSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT) &&
                            accountExitTextSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT) &&
                            starSkinTextSwitch.isChecked ||
                            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT) &&
                            freeDataCardTextSwitch.isChecked
                    prefs.edit()
                        .putBoolean(ConfigManager.KEY_MY_PAGE_CUSTOMIZE, hasEnabledMyPageOption)
                        .putBoolean(ConfigManager.KEY_HIDE_RENEW_BUTTON, renewSwitch.isChecked)
                        .putBoolean(ConfigManager.KEY_REMOVE_GAME_CENTER, gameCenterSwitch.isChecked)
                        .putBoolean(ConfigManager.KEY_REMOVE_ABOUT_ME_BANNER, bannerSwitch.isChecked)
                        .putBoolean(ConfigManager.KEY_REMOVE_MY_SERVICE, serviceSwitch.isChecked)
                        .putBoolean(
                            ConfigManager.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE,
                            coinCenterBubbleSwitch.isChecked,
                        )
                        .putBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_SIGN_IN_DOT, signInDotSwitch.isChecked)
                        .putBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_AI_COIN_ASSET, aiCoinAssetSwitch.isChecked)
                        .putBoolean(
                            ConfigManager.KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT,
                            manageSpaceTextSwitch.isChecked,
                        )
                        .putBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_REWARD_TEXT, rewardTextSwitch.isChecked)
                        .putBoolean(
                            ConfigManager.KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT,
                            accountExitTextSwitch.isChecked,
                        )
                        .putBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT, starSkinTextSwitch.isChecked)
                        .putBoolean(
                            ConfigManager.KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT,
                            freeDataCardTextSwitch.isChecked,
                        )
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.MY_PAGE_CUSTOMIZE_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            showStableSubDialog(dialog, density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showMyPageCustomizeDialog failed: ${t.message}")
        }
    }

    private fun showMemberCardCustomizeDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            var backgroundUriCleared = false
            val backgroundImageControl = createMemberCardBackgroundImageRow(
                context = context,
                prefs = prefs,
                padding = padding,
                onChoose = {
                    backgroundUriCleared = false
                    launchMemberCardBackgroundPicker(context)
                },
                onAdjust = {
                    val uriString = prefs.getString(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_URI, null)
                    if (uriString.isNullOrBlank() || backgroundUriCleared) {
                        Toast.makeText(
                            context,
                            UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_FAILED,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        showMemberCardBackgroundEditorDialog(context, uriString)
                    }
                },
                onClear = {
                    backgroundUriCleared = true
                    Toast.makeText(
                        context,
                        UiText.Settings.MEMBER_CARD_BACKGROUND_CLEAR_PENDING,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
            val blurSlider = createIntSliderRow(
                context = context,
                label = UiText.Settings.MEMBER_CARD_BACKGROUND_BLUR_LABEL,
                description = UiText.Settings.MEMBER_CARD_BACKGROUND_BLUR_DESC,
                padding = padding,
                minValue = MEMBER_CARD_BACKGROUND_BLUR_MIN,
                maxValue = MEMBER_CARD_BACKGROUND_BLUR_MAX,
                initialValue = prefs.getInt(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS, 0),
                valueFormatter = { value ->
                    if (value == 0) UiText.Settings.MEMBER_CARD_BACKGROUND_BLUR_NONE else value.toString()
                },
            )
            val sizeRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.MEMBER_CARD_SIZE_ADJUST_LABEL,
                UiText.Settings.MEMBER_CARD_SIZE_ADJUST_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_MEMBER_CARD_SIZE_ADJUST, false),
            )
            val widthSlider = createIntSliderRow(
                context = context,
                label = UiText.Settings.MEMBER_CARD_WIDTH_LABEL,
                description = UiText.Settings.MEMBER_CARD_SIZE_ADJUST_DESC,
                padding = padding,
                minValue = MEMBER_CARD_SIZE_WIDTH_MIN,
                maxValue = MEMBER_CARD_SIZE_WIDTH_MAX,
                initialValue = prefs.getInt(ConfigManager.KEY_MEMBER_CARD_SIZE_WIDTH_DP, 0),
                valueFormatter = { value ->
                    if (value == 0) UiText.Settings.MEMBER_CARD_SIZE_DEFAULT else "${value}dp"
                },
            )
            val heightSlider = createIntSliderRow(
                context = context,
                label = UiText.Settings.MEMBER_CARD_SIZE_HEIGHT_LABEL,
                description = UiText.Settings.MEMBER_CARD_SIZE_ADJUST_DESC,
                padding = padding,
                minValue = MEMBER_CARD_SIZE_HEIGHT_MIN,
                maxValue = MEMBER_CARD_SIZE_HEIGHT_MAX,
                initialValue = prefs.getInt(ConfigManager.KEY_MEMBER_CARD_SIZE_HEIGHT_DP, 0),
                valueFormatter = { value ->
                    if (value == 0) UiText.Settings.MEMBER_CARD_SIZE_DEFAULT else "${value}dp"
                },
            )
            val operationRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_MEMBER_CARD_OPERATION_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_OPERATION_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_MEMBER_CARD_OPERATION, false),
            )
            val benefitRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_MEMBER_CARD_BENEFIT_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_BENEFIT_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_MEMBER_CARD_BENEFIT, false),
            )
            val benefitBarRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_MEMBER_CARD_BENEFIT_BAR_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_BENEFIT_BAR_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_MEMBER_CARD_BENEFIT_BAR, false),
            )
            val svipLevelRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_MEMBER_CARD_SVIP_LEVEL_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_SVIP_LEVEL_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_MEMBER_CARD_SVIP_LEVEL, false),
            )
            val svipStatusRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_MEMBER_CARD_SVIP_STATUS_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_SVIP_STATUS_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_MEMBER_CARD_SVIP_STATUS, false),
            )
            val renewButtonRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.HIDE_MEMBER_CARD_RENEW_BUTTON_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_RENEW_BUTTON_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_HIDE_MEMBER_CARD_RENEW_BUTTON, false),
            )
            val removeCardClickRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.REMOVE_MEMBER_CARD_CLICK_LABEL,
                UiText.Settings.REMOVE_MEMBER_CARD_CLICK_DESC,
                null,
                padding,
                true,
                prefs.getBoolean(ConfigManager.KEY_REMOVE_MEMBER_CARD_CLICK, false),
            )
            val canViewBackgroundOnClick =
                prefs.getBoolean(ConfigManager.KEY_REPLACE_MEMBER_CARD_BACKGROUND, false) &&
                    !prefs.getString(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_URI, null).isNullOrBlank()
            val viewBackgroundOnClickRow = createSwitchRow(
                context,
                prefs,
                UiText.Settings.VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK_LABEL,
                UiText.Settings.VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK_DESC,
                null,
                padding,
                canViewBackgroundOnClick,
                canViewBackgroundOnClick &&
                    !prefs.getBoolean(ConfigManager.KEY_REMOVE_MEMBER_CARD_CLICK, false) &&
                    prefs.getBoolean(ConfigManager.KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK, false),
            )

            root.addView(backgroundImageControl.row)
            root.addView(blurSlider.row)
            root.addView(sizeRow)
            root.addView(widthSlider.row)
            root.addView(heightSlider.row)
            root.addView(createDivider(context, padding))
            root.addView(removeCardClickRow)
            root.addView(viewBackgroundOnClickRow)
            root.addView(createDivider(context, padding))
            root.addView(createCustomHideWidgetSectionTitle(context, padding))
            root.addView(operationRow)
            root.addView(benefitRow)
            root.addView(benefitBarRow)
            root.addView(svipLevelRow)
            root.addView(svipStatusRow)
            root.addView(renewButtonRow)

            val backgroundSwitch = backgroundImageControl.switch
            val operationSwitch = findSwitchView(operationRow)
            val sizeSwitch = findSwitchView(sizeRow)
            val benefitSwitch = findSwitchView(benefitRow)
            val benefitBarSwitch = findSwitchView(benefitBarRow)
            val svipLevelSwitch = findSwitchView(svipLevelRow)
            val svipStatusSwitch = findSwitchView(svipStatusRow)
            val renewButtonSwitch = findSwitchView(renewButtonRow)
            val removeCardClickSwitch = findSwitchView(removeCardClickRow)
            val viewBackgroundOnClickSwitch = findSwitchView(viewBackgroundOnClickRow)
            if (
                sizeSwitch == null ||
                operationSwitch == null ||
                benefitSwitch == null ||
                benefitBarSwitch == null ||
                svipLevelSwitch == null ||
                svipStatusSwitch == null ||
                renewButtonSwitch == null ||
                removeCardClickSwitch == null ||
                viewBackgroundOnClickSwitch == null
            ) {
                XposedCompat.logW("[SettingsMenuHook] showMemberCardCustomizeDialog failed: switch view missing")
                return
            }

            fun canUseViewBackgroundClick(): Boolean {
                return backgroundSwitch.isChecked &&
                    !backgroundUriCleared &&
                    !prefs.getString(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_URI, null).isNullOrBlank()
            }

            fun updateViewBackgroundClickRow() {
                val enabled = canUseViewBackgroundClick()
                setSwitchRowEnabled(viewBackgroundOnClickRow, enabled)
                if (!enabled) {
                    viewBackgroundOnClickSwitch.isChecked = false
                }
            }

            val selectionListener: (String) -> Unit = { uriString ->
                backgroundUriCleared = false
                backgroundSwitch.isChecked = true
                backgroundImageControl.updateSelectedUri(uriString)
                updateViewBackgroundClickRow()
            }
            memberCardBackgroundSelectionListener = selectionListener

            backgroundSwitch.setOnCheckedChangeListener { _, _ ->
                updateViewBackgroundClickRow()
            }
            removeCardClickSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && viewBackgroundOnClickSwitch.isChecked) {
                    viewBackgroundOnClickSwitch.isChecked = false
                }
            }
            viewBackgroundOnClickSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && removeCardClickSwitch.isChecked) {
                    removeCardClickSwitch.isChecked = false
                }
            }
            updateViewBackgroundClickRow()

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setTitle(UiText.Settings.MEMBER_CARD_CUSTOMIZE_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val hasEnabledMemberCardOption =
                        backgroundSwitch.isChecked ||
                            sizeSwitch.isChecked ||
                            operationSwitch.isChecked ||
                            benefitSwitch.isChecked ||
                            benefitBarSwitch.isChecked ||
                            svipLevelSwitch.isChecked ||
                            svipStatusSwitch.isChecked ||
                            renewButtonSwitch.isChecked ||
                            removeCardClickSwitch.isChecked ||
                            viewBackgroundOnClickSwitch.isChecked
                    val viewBackgroundOnClickEnabled =
                        !removeCardClickSwitch.isChecked &&
                            canUseViewBackgroundClick() &&
                            viewBackgroundOnClickSwitch.isChecked
                    val editor = prefs.edit()
                        .putBoolean(ConfigManager.KEY_MEMBER_CARD_CUSTOMIZE, hasEnabledMemberCardOption)
                        .putBoolean(
                            ConfigManager.KEY_REPLACE_MEMBER_CARD_BACKGROUND,
                            backgroundSwitch.isChecked,
                        )
                        .putInt(
                            ConfigManager.KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS,
                            blurSlider.getValue(),
                        )
                        .putBoolean(
                            ConfigManager.KEY_MEMBER_CARD_SIZE_ADJUST,
                            sizeSwitch.isChecked,
                        )
                        .putInt(
                            ConfigManager.KEY_MEMBER_CARD_SIZE_WIDTH_DP,
                            widthSlider.getValue(),
                        )
                        .putInt(
                            ConfigManager.KEY_MEMBER_CARD_SIZE_HEIGHT_DP,
                            heightSlider.getValue(),
                        )
                        .putBoolean(
                            ConfigManager.KEY_HIDE_MEMBER_CARD_OPERATION,
                            operationSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_HIDE_MEMBER_CARD_BENEFIT,
                            benefitSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_HIDE_MEMBER_CARD_BENEFIT_BAR,
                            benefitBarSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_HIDE_MEMBER_CARD_SVIP_LEVEL,
                            svipLevelSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_HIDE_MEMBER_CARD_SVIP_STATUS,
                            svipStatusSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_HIDE_MEMBER_CARD_RENEW_BUTTON,
                            renewButtonSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_REMOVE_MEMBER_CARD_CLICK,
                            removeCardClickSwitch.isChecked,
                        )
                        .putBoolean(
                            ConfigManager.KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK,
                            viewBackgroundOnClickEnabled,
                        )
                    if (backgroundUriCleared) {
                        editor.remove(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_URI)
                            .remove(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT)
                            .remove(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES)
                            .remove(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE)
                            .remove(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE)
                    }
                    editor.apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.MEMBER_CARD_CUSTOMIZE_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.setOnDismissListener {
                if (memberCardBackgroundSelectionListener === selectionListener) {
                    memberCardBackgroundSelectionListener = null
                }
            }
            showStableSubDialog(dialog, density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showMemberCardCustomizeDialog failed: ${t.message}")
        }
    }

    private fun showMemberCardBackgroundEditorDialog(
        context: Context,
        uriString: String,
    ) {
        try {
            val prefs = ConfigManager.getPrefs(context)
            val editorAspectRatio = memberCardBackgroundEditorAspectRatio(context, prefs)
            val bitmap = loadMemberCardBackgroundPreviewBitmap(context, uriString, editorAspectRatio) ?: run {
                Toast.makeText(
                    context,
                    UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_FAILED,
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()
            val tokens = UiStyle.tokens(context)

            val editorView = MemberCardBackgroundEditorView(context, bitmap, editorAspectRatio).apply {
                scalePercent = prefs.getInt(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT, 100)
                rotationDegrees = prefs.getInt(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES, 0)
                offsetXPermille = prefs.getInt(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE, 0)
                offsetYPermille = prefs.getInt(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE, 0)
            }

            val scaleValue = TextView(context).apply {
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.accent)
                gravity = Gravity.END
                includeFontPadding = false
            }
            fun updateScaleValue(value: Int) {
                scaleValue.text = "${value.coerceIn(100, 300)}%"
            }
            updateScaleValue(editorView.scalePercent)
            var scaleSeekBar: SeekBar? = null
            editorView.onScalePercentChanged = { value ->
                updateScaleValue(value)
                val progress = value.coerceIn(100, 300) - 100
                if (scaleSeekBar?.progress != progress) {
                    scaleSeekBar?.progress = progress
                }
            }

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)

                addView(TextView(context).apply {
                    text = UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_HINT
                    textSize = 12.5f
                    setTextColor(tokens.textSecondary)
                    includeFontPadding = false
                    setLineSpacing(1f * density, 1f)
                    setPadding(0, 0, 0, (10 * density).toInt())
                })

                addView(
                    editorView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )

                val scaleHeader = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (14 * density).toInt(), 0, 0)
                    addView(TextView(context).apply {
                        text = UiText.Settings.MEMBER_CARD_BACKGROUND_SCALE_LABEL
                        textSize = 14.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.textPrimary)
                        includeFontPadding = false
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(scaleValue, LinearLayout.LayoutParams((64 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
                }
                addView(scaleHeader)

                addView(SeekBar(context).apply {
                    scaleSeekBar = this
                    max = 200
                    progress = editorView.scalePercent.coerceIn(100, 300) - 100
                    splitTrack = false
                    val horizontalInset = (12 * density).toInt()
                    setPadding(horizontalInset, (4 * density).toInt(), horizontalInset, 0)
                    applyMemberCardSeekBarTint(this, tokens)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            val value = (100 + progress).coerceIn(100, 300)
                            editorView.scalePercent = value
                            updateScaleValue(value)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                    })
                })

                addView(Button(context).apply {
                    text = UiText.Settings.MEMBER_CARD_BACKGROUND_ROTATE
                    UiStyle.paintScanActionButton(this, density, tokens.accent)
                    setOnClickListener {
                        editorView.rotationDegrees = editorView.rotationDegrees + 90
                    }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (40 * density).toInt(),
                ).apply {
                    topMargin = (10 * density).toInt()
                    gravity = Gravity.END
                })
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setTitle(UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_TITLE)
                .setView(root)
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE) { _, _ ->
                    prefs.edit()
                        .putInt(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT, editorView.scalePercent)
                        .putInt(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES, editorView.rotationDegrees)
                        .putInt(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE, editorView.offsetXPermille)
                        .putInt(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE, editorView.offsetYPermille)
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_SAVED),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .create()
            showStableSubDialog(dialog, density)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] show background editor failed: ${t.message}")
            Toast.makeText(
                context,
                UiText.Settings.MEMBER_CARD_BACKGROUND_EDITOR_FAILED,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun loadMemberCardBackgroundPreviewBitmap(
        context: Context,
        uriString: String,
        cropAspectRatio: Float,
    ): Bitmap? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val resolver = context.contentResolver ?: return null
        val metrics = context.resources.displayMetrics
        val targetWidth = metrics.widthPixels.coerceAtMost(1600).coerceAtLeast(720)
        val targetHeight = (targetWidth / cropAspectRatio.coerceIn(0.35f, 6f))
            .toInt()
            .coerceAtLeast(240)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            options.inSampleSize = calculateBitmapInSampleSize(
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
            XposedCompat.logW("[SettingsMenuHook] load background preview failed: ${t.message}")
            null
        }
    }

    private fun memberCardBackgroundEditorAspectRatio(
        context: Context,
        prefs: android.content.SharedPreferences,
    ): Float {
        val statePrefs = ConfigManager.getModuleStatePrefs(context)
        val defaultWidthPx = statePrefs.getInt(ConfigManager.KEY_MEMBER_CARD_DEFAULT_WIDTH_PX, 0)
        val defaultHeightPx = statePrefs.getInt(ConfigManager.KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX, 0)
        val defaultAspectRatio = if (defaultWidthPx > 0 && defaultHeightPx > 0) {
            defaultWidthPx.toFloat() / defaultHeightPx.toFloat()
        } else {
            3f
        }.coerceIn(0.35f, 6f)

        if (!prefs.getBoolean(ConfigManager.KEY_MEMBER_CARD_SIZE_ADJUST, false)) return defaultAspectRatio
        val widthDp = prefs.getInt(ConfigManager.KEY_MEMBER_CARD_SIZE_WIDTH_DP, 0)
        val heightDp = prefs.getInt(ConfigManager.KEY_MEMBER_CARD_SIZE_HEIGHT_DP, 0)
        val density = context.resources.displayMetrics.density
        val effectiveWidthPx = when {
            widthDp > 0 -> widthDp * density
            defaultWidthPx > 0 -> defaultWidthPx.toFloat()
            heightDp > 0 -> heightDp * density * defaultAspectRatio
            else -> defaultAspectRatio
        }
        val effectiveHeightPx = when {
            heightDp > 0 -> heightDp * density
            defaultHeightPx > 0 -> defaultHeightPx.toFloat()
            widthDp > 0 -> widthDp * density / defaultAspectRatio
            else -> 1f
        }
        return if (effectiveWidthPx > 0f && effectiveHeightPx > 0f) {
            effectiveWidthPx / effectiveHeightPx
        } else {
            defaultAspectRatio
        }.coerceIn(0.35f, 6f)
    }

    private fun calculateBitmapInSampleSize(
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

    private fun buildVersionDisplayInfo(context: Context): VersionDisplayInfo {
        val hostVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                ?: UiText.Settings.UNKNOWN
        } catch (_: Exception) {
            UiText.Settings.UNKNOWN
        }
        val moduleVersion = try {
            com.xiyunmn.puredupan.hook.BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            UiText.Settings.UNKNOWN
        }
        return VersionDisplayInfo(
            hostVersion = hostVersion,
            hostBuildType = "",
            moduleVersion = moduleVersion,
            moduleBuildType = if (com.xiyunmn.puredupan.hook.BuildConfig.DEBUG) {
                UiText.Settings.MODULE_DEBUG_VERSION
            } else {
                UiText.Settings.MODULE_RELEASE_VERSION
            },
        )
    }

    private fun resolveSwitchRuntimeSupport(item: SwitchItem): SwitchRuntimeSupport {
        return SwitchRuntimeSupport(
            supported = item.supported,
            partial = false,
            note = null,
        )
    }

    private fun hostCapabilities(context: Context) =
        HostRegistry.requireByPackageName(context.packageName).capabilities

    private fun isFeatureVisible(context: Context, featureKey: String?): Boolean {
        if (featureKey.isNullOrBlank()) return true
        return ConfigManager.isFeatureAvailable(featureKey)
    }

    private fun visibleRows(
        context: Context,
        vararg rows: Pair<String?, View>,
    ): List<View> {
        return rows.filter { (featureKey, _) -> isFeatureVisible(context, featureKey) }
            .map { (_, view) -> view }
    }

    private fun addVisibleRows(
        root: LinearLayout,
        rows: List<View>,
    ) {
        rows.forEach(root::addView)
    }

    private fun addTitledSection(
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

    private fun restartHostApp(context: Context) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                )
                context.startActivity(launchIntent)
            } else {
                XposedCompat.logW("[SettingsMenuHook] restart: no launch intent for ${context.packageName}")
            }
        } catch (t: Throwable) {
            XposedCompat.log("[SettingsMenuHook] restart launch failed: ${t.message}")
            XposedCompat.log(t)
            return
        }
        try {
            Runtime.getRuntime().exit(0)
        } catch (t: Throwable) {
            XposedCompat.logD("SettingsMenuHook: ${t.message}")
        }
        try {
            kotlin.system.exitProcess(0)
        } catch (t: Throwable) {
            XposedCompat.logD("SettingsMenuHook: ${t.message}")
        }
    }

    private fun dialogThemeFor(context: Context): Int {
        return if (UiStyle.tokens(context).night) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
    }

    internal fun getDialogTheme(context: Context): Int = dialogThemeFor(context)

    internal fun applyDialogStyle(window: Window, density: Float) {
        val tokens = UiStyle.tokens(window.context)
        UiStyle.applyDialogCard(window, tokens)
        clearSystemDialogCustomPanelPadding(window)
        applyStableDialogWindowLayout(window, density)
    }

    private fun applyUnifiedDialogCardStyle(window: Window, density: Float) {
        val tokens = UiStyle.tokens(window.context)
        UiStyle.applyDialogCard(window, tokens)
        clearSystemDialogCustomPanelPadding(window)
        applyStableDialogWindowLayout(window, density)
    }

    private fun createDialogScrollContainer(context: Context, content: View): ScrollView {
        return ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
        }
    }

    private fun hasAnyMemberCardCustomizeOptionEnabled(
        context: Context,
        prefs: android.content.SharedPreferences,
    ): Boolean {
        val hasViewBackgroundClick =
            isFeatureVisible(context, ConfigManager.KEY_REMOVE_MEMBER_CARD_CLICK) &&
                isFeatureVisible(context, ConfigManager.KEY_REPLACE_MEMBER_CARD_BACKGROUND) &&
                !prefs.getBoolean(ConfigManager.KEY_REMOVE_MEMBER_CARD_CLICK, false) &&
                prefs.getBoolean(ConfigManager.KEY_REPLACE_MEMBER_CARD_BACKGROUND, false) &&
                !prefs.getString(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_URI, null).isNullOrBlank() &&
                isFeatureVisible(context, ConfigManager.KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK) &&
                prefs.getBoolean(ConfigManager.KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK, false)
        return isFeatureVisible(context, ConfigManager.KEY_REPLACE_MEMBER_CARD_BACKGROUND) &&
            prefs.getBoolean(ConfigManager.KEY_REPLACE_MEMBER_CARD_BACKGROUND, false) ||
            isFeatureVisible(context, ConfigManager.KEY_MEMBER_CARD_SIZE_ADJUST) &&
            prefs.getBoolean(ConfigManager.KEY_MEMBER_CARD_SIZE_ADJUST, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_MEMBER_CARD_OPERATION) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_MEMBER_CARD_OPERATION, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_MEMBER_CARD_BENEFIT) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_MEMBER_CARD_BENEFIT, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_MEMBER_CARD_BENEFIT_BAR) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_MEMBER_CARD_BENEFIT_BAR, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_MEMBER_CARD_SVIP_LEVEL) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_MEMBER_CARD_SVIP_LEVEL, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_MEMBER_CARD_SVIP_STATUS) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_MEMBER_CARD_SVIP_STATUS, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_MEMBER_CARD_RENEW_BUTTON) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_MEMBER_CARD_RENEW_BUTTON, false) ||
            isFeatureVisible(context, ConfigManager.KEY_REMOVE_MEMBER_CARD_CLICK) &&
            prefs.getBoolean(ConfigManager.KEY_REMOVE_MEMBER_CARD_CLICK, false) ||
            hasViewBackgroundClick
    }

    private fun hasAnyHomeCustomizeOptionEnabled(
        context: Context,
        prefs: android.content.SharedPreferences,
    ): Boolean {
        return isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_TOP_PROMOTION) &&
            ConfigManager.readHomeTopPromotionHidden(prefs) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_SEARCH_PLACEHOLDER) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_SEARCH_PLACEHOLDER, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_SEARCH_AIGC_ICON) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_SEARCH_AIGC_ICON, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_FEED_TIP) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_FEED_TIP, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_BANNER) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_BANNER, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_MEMORIES_SECTION) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_MEMORIES_SECTION, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_SAVE_SECTION) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_SAVE_SECTION, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_HOME_RECENT_SECTION) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_HOME_RECENT_SECTION, false)
    }

    private fun hasAnySharePageCustomizeOptionEnabled(
        context: Context,
        prefs: android.content.SharedPreferences,
    ): Boolean {
        return isFeatureVisible(context, ConfigManager.KEY_REMOVE_HOME_FAB) &&
            prefs.getBoolean(ConfigManager.KEY_REMOVE_HOME_FAB, false)
    }

    private fun hasAnyMyPageCustomizeOptionEnabled(
        context: Context,
        prefs: android.content.SharedPreferences,
    ): Boolean {
        return isFeatureVisible(context, ConfigManager.KEY_HIDE_RENEW_BUTTON) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_RENEW_BUTTON, false) ||
            isFeatureVisible(context, ConfigManager.KEY_REMOVE_GAME_CENTER) &&
            prefs.getBoolean(ConfigManager.KEY_REMOVE_GAME_CENTER, false) ||
            isFeatureVisible(context, ConfigManager.KEY_REMOVE_ABOUT_ME_BANNER) &&
            prefs.getBoolean(ConfigManager.KEY_REMOVE_ABOUT_ME_BANNER, false) ||
            isFeatureVisible(context, ConfigManager.KEY_REMOVE_MY_SERVICE) &&
            prefs.getBoolean(ConfigManager.KEY_REMOVE_MY_SERVICE, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_SIGN_IN_DOT) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_SIGN_IN_DOT, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_AI_COIN_ASSET) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_AI_COIN_ASSET, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_REWARD_TEXT) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_REWARD_TEXT, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT, false)
    }

    private fun hasAnyBottomBarCustomizeOptionEnabled(
        context: Context,
        prefs: android.content.SharedPreferences,
    ): Boolean {
        return isFeatureVisible(context, ConfigManager.KEY_REPLACE_BOTTOM_AI) &&
            prefs.getBoolean(ConfigManager.KEY_REPLACE_BOTTOM_AI, false) ||
            isFeatureVisible(context, ConfigManager.KEY_BLOCK_BOTTOM_BADGE) &&
            prefs.getBoolean(ConfigManager.KEY_BLOCK_BOTTOM_BADGE, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_TAB_FILE) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_TAB_FILE, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_TAB_SHARE) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_TAB_SHARE, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_TAB_VIP) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_TAB_VIP, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_TAB_AIGC) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_TAB_AIGC, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_TAB_HOME) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_TAB_HOME, false) ||
            isFeatureVisible(context, ConfigManager.KEY_HIDE_TAB_MINE) &&
            prefs.getBoolean(ConfigManager.KEY_HIDE_TAB_MINE, false)
    }

    private fun hasAnyPerformanceOptimizeOptionEnabled(
        context: Context,
        prefs: android.content.SharedPreferences,
    ): Boolean {
        return isFeatureVisible(context, ConfigManager.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER) &&
            prefs.getBoolean(ConfigManager.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER, false) ||
            isFeatureVisible(context, ConfigManager.KEY_DISABLE_DATAPACK_SOCKET_REGISTER) &&
            prefs.getBoolean(ConfigManager.KEY_DISABLE_DATAPACK_SOCKET_REGISTER, false) ||
            isFeatureVisible(context, ConfigManager.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT) &&
            prefs.getBoolean(ConfigManager.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT, false) ||
            isFeatureVisible(context, ConfigManager.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD) &&
            prefs.getBoolean(ConfigManager.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD, false) ||
            isFeatureVisible(context, ConfigManager.KEY_DISABLE_OEM_PUSH_SERVICE) &&
            prefs.getBoolean(ConfigManager.KEY_DISABLE_OEM_PUSH_SERVICE, false) ||
            isFeatureVisible(context, ConfigManager.KEY_DISABLE_VIDEO_AD_PRELOAD) &&
            prefs.getBoolean(ConfigManager.KEY_DISABLE_VIDEO_AD_PRELOAD, false) ||
            isFeatureVisible(context, ConfigManager.KEY_DISABLE_AD_SDK_INIT) &&
            prefs.getBoolean(ConfigManager.KEY_DISABLE_AD_SDK_INIT, false) ||
            isFeatureVisible(context, ConfigManager.KEY_DISABLE_SWAN_PRELOAD) &&
            prefs.getBoolean(ConfigManager.KEY_DISABLE_SWAN_PRELOAD, false) ||
            isFeatureVisible(context, ConfigManager.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE) &&
            prefs.getBoolean(ConfigManager.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE, false) ||
            isFeatureVisible(context, ConfigManager.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE) &&
            prefs.getBoolean(ConfigManager.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE, false) ||
            isFeatureVisible(context, ConfigManager.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART) &&
            prefs.getBoolean(ConfigManager.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART, false) ||
            isFeatureVisible(context, ConfigManager.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD) &&
            prefs.getBoolean(ConfigManager.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD, false) ||
            isFeatureVisible(context, ConfigManager.KEY_DISABLE_B2F_GUIDANCE_PREFETCH) &&
            prefs.getBoolean(ConfigManager.KEY_DISABLE_B2F_GUIDANCE_PREFETCH, false)
    }

    private fun createMemberCardBackgroundImageRow(
        context: Context,
        prefs: android.content.SharedPreferences,
        padding: Int,
        onChoose: () -> Unit,
        onAdjust: () -> Unit,
        onClear: () -> Unit,
    ): MemberCardBackgroundImageControl {
        val tokens = UiStyle.tokens(context)
        val density = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (padding * 0.5f).toInt(), 0, (padding * 0.55f).toInt())
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        textContainer.addView(TextView(context).apply {
            text = UiText.Settings.REPLACE_MEMBER_CARD_BACKGROUND_LABEL
            textSize = 15f
            setTextColor(tokens.textPrimary)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        textContainer.addView(TextView(context).apply {
            text = UiText.Settings.REPLACE_MEMBER_CARD_BACKGROUND_DESC
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(0, (3 * density).toInt(), (12 * density).toInt(), 0)
            includeFontPadding = false
            setLineSpacing(1f * density, 1f)
        })
        header.addView(
            textContainer,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f),
        )

        val enabledSwitch = Switch(context).apply {
            isChecked = prefs.getBoolean(ConfigManager.KEY_REPLACE_MEMBER_CARD_BACKGROUND, false)
            applyMemberCardSwitchTint(this, tokens)
        }
        header.addView(
            enabledSwitch,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(header)

        val controlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        val imageUri = prefs.getString(ConfigManager.KEY_MEMBER_CARD_BACKGROUND_URI, null)
        val display = TextView(context).apply {
            text = memberCardBackgroundDisplayText(imageUri)
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setTextColor(tokens.textPrimary)
            includeFontPadding = false
            background = UiStyle.createPlainInputUnderlineBackground(tokens, density)
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            setOnClickListener {
                enabledSwitch.isChecked = true
                onChoose()
            }
        }
        controlRow.addView(
            display,
            LinearLayout.LayoutParams(0, (40 * density).toInt(), 1.0f),
        )

        val chooseButton = Button(context).apply {
            text = UiText.Settings.MEMBER_CARD_BACKGROUND_CHOOSE
            UiStyle.paintScanActionButton(this, density, tokens.accent)
            setOnClickListener {
                enabledSwitch.isChecked = true
                onChoose()
            }
        }
        controlRow.addView(
            chooseButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (40 * density).toInt(),
            ).apply {
                leftMargin = (8 * density).toInt()
            },
        )

        val adjustButton = Button(context).apply {
            text = UiText.Settings.MEMBER_CARD_BACKGROUND_ADJUST
            UiStyle.paintScanActionButton(this, density, tokens.accent)
            updateButtonEnabledState(!imageUri.isNullOrBlank())
            setOnClickListener {
                enabledSwitch.isChecked = true
                onAdjust()
            }
        }
        controlRow.addView(
            adjustButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (40 * density).toInt(),
            ).apply {
                leftMargin = (4 * density).toInt()
            },
        )

        val clearButton = Button(context).apply {
            text = UiText.Settings.MEMBER_CARD_BACKGROUND_CLEAR
            UiStyle.paintScanActionButton(this, density, tokens.textSecondary)
            setOnClickListener {
                enabledSwitch.isChecked = false
                display.text = memberCardBackgroundDisplayText(null)
                adjustButton.updateButtonEnabledState(false)
                onClear()
            }
        }
        controlRow.addView(
            clearButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (40 * density).toInt(),
            ).apply {
                leftMargin = (4 * density).toInt()
            },
        )
        root.addView(controlRow)

        return MemberCardBackgroundImageControl(
            row = root,
            switch = enabledSwitch,
            updateSelectedUri = { uri ->
                display.text = memberCardBackgroundDisplayText(uri)
                adjustButton.updateButtonEnabledState(!uri.isNullOrBlank())
            },
        )
    }

    private fun memberCardBackgroundDisplayText(uri: String?): String {
        if (uri.isNullOrBlank()) return UiText.Settings.MEMBER_CARD_BACKGROUND_NONE
        val name = runCatching {
            android.net.Uri.parse(uri).lastPathSegment
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
            ?: uri.takeLast(36)
        return UiText.Settings.memberCardBackgroundSelected(name)
    }

    private fun applyMemberCardSwitchTint(
        switch: Switch,
        tokens: UiStyle.Tokens,
    ) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked),
        )
        switch.thumbTintList = ColorStateList(states, intArrayOf(tokens.accent, tokens.accentThumbOff))
        switch.trackTintList = ColorStateList(states, intArrayOf(tokens.accentTrackOn, tokens.accentTrackOff))
    }

    private fun applyMemberCardSeekBarTint(
        seekBar: SeekBar,
        tokens: UiStyle.Tokens,
    ) {
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_enabled),
        )
        seekBar.progressTintList = ColorStateList(states, intArrayOf(tokens.textMuted, tokens.accent))
        seekBar.thumbTintList = ColorStateList(states, intArrayOf(tokens.textMuted, tokens.accent))
        seekBar.progressBackgroundTintList = ColorStateList(states, intArrayOf(tokens.divider, tokens.inputStroke))
        seekBar.secondaryProgressTintList = ColorStateList(states, intArrayOf(tokens.divider, tokens.inputStroke))
    }

    private fun createIntSliderRow(
        context: Context,
        label: String,
        description: String,
        padding: Int,
        minValue: Int,
        maxValue: Int,
        initialValue: Int,
        valueFormatter: (Int) -> String,
    ): IntSliderControl {
        val tokens = UiStyle.tokens(context)
        val density = context.resources.displayMetrics.density
        val currentValue = intArrayOf(initialValue.coerceIn(minValue, maxValue))
        val valueText = TextView(context).apply {
            textSize = 13f
            setTextColor(tokens.accent)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            includeFontPadding = false
            setPadding((8 * density).toInt(), 0, 0, 0)
        }

        fun updateValueText(value: Int) {
            valueText.text = valueFormatter(value)
        }
        updateValueText(currentValue[0])

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            setPadding(0, (padding * 0.5f).toInt(), 0, (padding * 0.55f).toInt())

            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            textContainer.addView(TextView(context).apply {
                    text = label
                    textSize = 15f
                    setTextColor(tokens.textPrimary)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setLineSpacing(1.5f * density, 1f)
                })
            textContainer.addView(TextView(context).apply {
                text = description
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setPadding(0, (3 * density).toInt(), (12 * density).toInt(), 0)
                includeFontPadding = false
                setLineSpacing(1f * density, 1f)
            })
            titleRow.addView(
                textContainer,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            )
            titleRow.addView(
                valueText,
                LinearLayout.LayoutParams((72 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(titleRow)

            addView(SeekBar(context).apply {
                max = (maxValue - minValue).coerceAtLeast(0)
                progress = currentValue[0] - minValue
                splitTrack = false
                val horizontalInset = (12 * density).toInt()
                setPadding(horizontalInset, (4 * density).toInt(), horizontalInset, 0)
                applyMemberCardSeekBarTint(this, tokens)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val value = (minValue + progress).coerceIn(minValue, maxValue)
                        currentValue[0] = value
                        updateValueText(value)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            })
        }

        return IntSliderControl(
            row = row,
            getValue = { currentValue[0] },
        )
    }

    @Suppress("DEPRECATION")
    private fun createSwitchRow(
        context: Context,
        prefs: android.content.SharedPreferences,
        label: String,
        description: String?,
        prefKey: String?,
        padding: Int,
        enabled: Boolean = true,
        defaultValue: Boolean = false,
        actionIcon: String? = null,
        onActionClick: (() -> Unit)? = null,
        linkedPrefKeys: List<String> = emptyList(),
        showSwitch: Boolean = true,
    ): View {
        val tokens = UiStyle.tokens(context)
        val density = context.resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (padding * 0.55f).toInt(), 0, (padding * 0.55f).toInt())
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tvLabel = TextView(context).apply {
            text = label
            textSize = 14.5f
            setTextColor(if (enabled) tokens.textPrimary else tokens.textMuted)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setLineSpacing(1.5f * density, 1f)
        }
        textContainer.addView(tvLabel)

        if (description != null) {
            val tvDesc = TextView(context).apply {
                text = description
                textSize = 11.5f
                setTextColor(if (enabled) tokens.textSecondary else tokens.textMuted)
                setPadding(0, (3 * density).toInt(), (14 * density).toInt(), 0)
                includeFontPadding = false
                setLineSpacing(1f * density, 1f)
            }
            textContainer.addView(tvDesc)
        }

        row.addView(
            textContainer,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
        )

        if (actionIcon != null && onActionClick != null) {
            val actionBtn = TextView(context).apply {
                text = actionIcon
                textSize = 18f
                setTextColor(if (enabled) tokens.accent else tokens.textMuted)
                gravity = Gravity.CENTER
                setPadding(
                    (12 * density).toInt(),
                    (6 * density).toInt(),
                    (12 * density).toInt(),
                    (6 * density).toInt()
                )
                setOnClickListener {
                    if (enabled) {
                        UiStyle.animateActionPress(this)
                        onActionClick()
                    }
                }
            }
            row.addView(actionBtn)
        }

        if (!showSwitch) {
            return row
        }

        val sw = Switch(context).apply {
            var reverting = false
            isChecked = if (enabled && prefKey != null) {
                resolveSwitchChecked(prefs, prefKey, linkedPrefKeys, defaultValue)
            } else {
                defaultValue
            }
            isEnabled = enabled

            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            )
            thumbTintList = ColorStateList(
                states, intArrayOf(
                    tokens.accent,
                    tokens.accentThumbOff,
                )
            )
            trackTintList = ColorStateList(
                states, intArrayOf(
                    tokens.accentTrackOn,
                    tokens.accentTrackOff,
                )
            )

            setOnCheckedChangeListener { _, isChecked ->
                if (enabled && !reverting) {
                    if (prefKey != null) {
                        val editor = prefs.edit().putBoolean(prefKey, isChecked)
                        for (linkedPrefKey in linkedPrefKeys) {
                            editor.putBoolean(linkedPrefKey, isChecked)
                        }
                        if (!editor.commit()) {
                            reverting = true
                            this.isChecked = !isChecked
                            reverting = false
                            Toast.makeText(
                                context,
                                UiText.Settings.SETTINGS_SAVE_FAILED,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
        row.addView(sw)

        return row
    }

    private fun resolveSwitchChecked(
        prefs: android.content.SharedPreferences,
        prefKey: String,
        linkedPrefKeys: List<String>,
        defaultValue: Boolean,
    ): Boolean {
        if (!prefs.contains(prefKey) && linkedPrefKeys.none { prefs.contains(it) }) {
            return defaultValue
        }
        if (prefs.getBoolean(prefKey, false)) return true
        return linkedPrefKeys.any { prefs.getBoolean(it, false) }
    }

    private fun findSwitchView(root: View): Switch? {
        if (root is Switch) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            val found = findSwitchView(root.getChildAt(index))
            if (found != null) return found
        }
        return null
    }

    private fun setSwitchRowEnabled(row: View, enabled: Boolean) {
        row.isEnabled = enabled
        row.alpha = if (enabled) 1f else 0.45f
        findSwitchView(row)?.isEnabled = enabled
    }

    private fun createCustomHideWidgetSectionTitle(context: Context, padding: Int): TextView {
        return createSectionTitle(context, padding, UiText.Settings.CUSTOM_HIDE_WIDGET_SECTION_TITLE)
    }

    private fun createCustomHideTextWidgetSectionTitle(context: Context, padding: Int): TextView {
        return createSectionTitle(context, padding, UiText.Settings.CUSTOM_HIDE_TEXT_WIDGET_SECTION_TITLE)
    }

    private fun createCustomHideSectionSectionTitle(context: Context, padding: Int): TextView {
        return createSectionTitle(context, padding, UiText.Settings.CUSTOM_HIDE_SECTION_SECTION_TITLE)
    }

    private fun createPerformanceSectionTitle(
        context: Context,
        padding: Int,
        text: String,
    ): TextView {
        return createSectionTitle(context, padding, text)
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

    private fun createAboutItem(
        context: Context,
        density: Float,
        padding: Int,
        title: String,
        content: String,
        url: String? = null,
        onClick: (() -> Unit)? = null,
    ): View {
        val tokens = UiStyle.tokens(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (padding * 0.4f).toInt(), 0, (padding * 0.4f).toInt())
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
            }

            addView(TextView(context).apply {
                text = title
                textSize = 14.5f
                setTextColor(tokens.textPrimary)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            })

            addView(TextView(context).apply {
                text = content
                textSize = 13f
                setTextColor(if (url != null) tokens.accent else tokens.textSecondary)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                includeFontPadding = false
                setPadding(0, (3 * density).toInt(), 0, 0)

                if (url != null) {
                    setOnClickListener {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (t: Throwable) {
                            XposedCompat.logW(
                                "[SettingsMenuHook] open about link failed: url=$url, msg=${t.message}"
                            )
                        }
                    }
                }
            })
        }
    }

    private fun createDivider(context: Context, padding: Int): View {
        val tokens = UiStyle.tokens(context)
        val density = context.resources.displayMetrics.density
        val divider = View(context)
        divider.setBackgroundColor(tokens.divider)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (0.8f * density).toInt().coerceAtLeast(1)
        )
        lp.setMargins(
            0,
            (padding * 0.4f).toInt(),
            0,
            (padding * 0.4f).toInt()
        )
        divider.layoutParams = lp
        return divider
    }

    private fun Button.updateButtonEnabledState(enabled: Boolean) {
        UiStyle.setButtonEnabledState(this, enabled)
    }

    private fun showStableSubDialog(dialog: AlertDialog, density: Float) {
        try {
            val preShowWindow = dialog.window ?: run {
                XposedCompat.logW("[SettingsMenuHook] showStableSubDialog aborted: window missing before show")
                return
            }
            applyStableSubDialogWindow(preShowWindow, density, clearCustomPadding = false)

            dialog.show()

            val window = dialog.window ?: run {
                XposedCompat.logW("[SettingsMenuHook] showStableSubDialog aborted: window missing after show")
                return
            }
            val decorView = window.decorView
            decorView.animate().cancel()
            decorView.alpha = 0f
            applyStableSubDialogWindow(window, density, clearCustomPadding = true)
            animateStableSubDialogEntry(decorView)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showStableSubDialog failed: ${t.message}")
            try {
                dialog.window?.decorView?.alpha = 1f
            } catch (_: Throwable) {
                // Best-effort visual recovery only.
            }
        }
    }

    private fun applyStableSubDialogWindow(
        window: Window,
        density: Float,
        clearCustomPadding: Boolean,
    ) {
        try {
            window.setGravity(Gravity.CENTER)
            window.setWindowAnimations(0)
            val tokens = UiStyle.tokens(window.context)
            UiStyle.applyDialogCard(window, tokens)
            applyStableDialogWindowLayout(window, density)
            if (clearCustomPadding) {
                clearSystemDialogCustomPanelPadding(window)
            }
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] applyStableSubDialogWindow failed: ${t.message}")
        }
    }

    private fun animateStableSubDialogEntry(root: View) {
        try {
            root.animate()
                .alpha(1f)
                .setDuration(200L)
                .setInterpolator(DecelerateInterpolator(1.15f))
                .start()
        } catch (t: Throwable) {
            root.alpha = 1f
            XposedCompat.logW("[SettingsMenuHook] stable sub dialog alpha animation failed: ${t.message}")
        }
    }

    private fun clearSystemDialogCustomPanelPadding(window: Window) {
        val customPanel = window.decorView.findViewById<View>(android.R.id.custom) ?: return
        if (
            customPanel.paddingLeft != 0 ||
            customPanel.paddingTop != 0 ||
            customPanel.paddingRight != 0 ||
            customPanel.paddingBottom != 0
        ) {
            customPanel.setPadding(0, 0, 0, 0)
        }
    }

    private fun applyStableDialogWindowLayout(window: Window, density: Float) {
        val screenWidth = window.context.resources.displayMetrics.widthPixels
        val horizontalMargin = (12f * density).toInt().coerceAtLeast(1)
        val availableWidth = screenWidth - horizontalMargin * 2
        if (availableWidth <= 0) return

        val maxWidth = (560f * density).toInt().coerceAtLeast(1)
        val minWidth = (280f * density).toInt().coerceAtMost(availableWidth)
        val targetWidth = availableWidth
            .coerceAtMost(maxWidth)
            .coerceAtLeast(minWidth)

        window.setGravity(Gravity.CENTER)
        val attrs = window.attributes
        attrs.gravity = Gravity.CENTER
        attrs.x = 0
        attrs.y = 0
        attrs.width = targetWidth
        attrs.height = ViewGroup.LayoutParams.WRAP_CONTENT
        window.attributes = attrs
        window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
