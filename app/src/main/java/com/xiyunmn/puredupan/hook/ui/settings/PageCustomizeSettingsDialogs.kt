package com.xiyunmn.puredupan.hook.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.settings.runtime.SettingsRuntimeSession
import com.xiyunmn.puredupan.hook.ui.UiText

internal object PageCustomizeSettingsDialogs {
    private const val LOG_TAG = "[PageCustomizeSettingsDialogs]"

    fun showFilePage(
        context: Context,
        prefs: SharedPreferences,
        settingsSession: SettingsRuntimeSession,
        texts: SettingsTextResolver,
    ) {
        try {
            if (!settingsSession.isFeatureVisible(SettingsUserState.KEY_FILE_PAGE_CUSTOMIZE)) {
                XposedCompat.logW("$LOG_TAG showFilePage skipped: unsupported host")
                return
            }
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val root = createDialogRoot(context, padding)
            val filePageItems = PageCustomizeSettingsItemsBuilder.filePageCustomizeItems(
                prefs = prefs,
                texts = texts,
                isFeatureVisible = settingsSession::isFeatureVisible,
            )
            val rowsByKey = createRowsByKey(context, prefs, padding, filePageItems)
            SettingsDialogLayout.addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = SettingsDialogLayout.createCustomHideWidgetSectionTitle(context, padding),
                rows = filePageItems.visibleRows(rowsByKey),
            )

            val switchesByKey = collectSwitchesByKey(rowsByKey)
            if (switchesByKey.values.any { it == null }) {
                XposedCompat.logW("$LOG_TAG showFilePage failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.FILE_PAGE_CUSTOMIZE_DIALOG_TITLE)
                .setView(SettingsDialogLayout.createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    PageCustomizeSettingsItemsBuilder.putFilePageCustomizeValues(
                        editor = prefs.edit(),
                        isFeatureVisible = settingsSession::isFeatureVisible,
                        isChecked = { key -> switchesByKey[key]?.isChecked == true },
                    ).apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.FILE_PAGE_CUSTOMIZE_SAVED),
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
            }
            SettingsDialogWindows.showStableSubDialog(dialog, density, LOG_TAG)
        } catch (t: Throwable) {
            XposedCompat.logW("$LOG_TAG showFilePage failed: ${t.message}")
        }
    }

    fun showDownloadPage(
        context: Context,
        prefs: SharedPreferences,
        settingsSession: SettingsRuntimeSession,
        texts: SettingsTextResolver,
    ) {
        try {
            if (!settingsSession.isFeatureVisible(SettingsUserState.KEY_DOWNLOAD_PAGE_CUSTOMIZE)) {
                XposedCompat.logW("$LOG_TAG showDownloadPage skipped: unsupported host")
                return
            }
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val root = createDialogRoot(context, padding)
            val downloadPageItems = PageCustomizeSettingsItemsBuilder.downloadPageCustomizeItems(
                prefs = prefs,
                texts = texts,
                isFeatureVisible = settingsSession::isFeatureVisible,
            )
            val rowsByKey = createRowsByKey(context, prefs, padding, downloadPageItems)
            SettingsDialogLayout.addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = SettingsDialogLayout.createCustomHideWidgetSectionTitle(context, padding),
                rows = downloadPageItems.visibleRows(rowsByKey),
            )

            val switchesByKey = collectSwitchesByKey(rowsByKey)
            if (switchesByKey.values.any { it == null }) {
                XposedCompat.logW("$LOG_TAG showDownloadPage failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.DOWNLOAD_PAGE_CUSTOMIZE_DIALOG_TITLE)
                .setView(SettingsDialogLayout.createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    PageCustomizeSettingsItemsBuilder.putDownloadPageCustomizeValues(
                        editor = prefs.edit(),
                        isFeatureVisible = settingsSession::isFeatureVisible,
                        isChecked = { key -> switchesByKey[key]?.isChecked == true },
                    ).apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.DOWNLOAD_PAGE_CUSTOMIZE_SAVED),
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
            }
            SettingsDialogWindows.showStableSubDialog(dialog, density, LOG_TAG)
        } catch (t: Throwable) {
            XposedCompat.logW("$LOG_TAG showDownloadPage failed: ${t.message}")
        }
    }

    fun showSearchPage(
        context: Context,
        prefs: SharedPreferences,
        settingsSession: SettingsRuntimeSession,
        texts: SettingsTextResolver,
    ) {
        try {
            if (!settingsSession.isFeatureVisible(SettingsUserState.KEY_SEARCH_PAGE_CUSTOMIZE)) {
                XposedCompat.logW("$LOG_TAG showSearchPage skipped: unsupported host")
                return
            }
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val root = createDialogRoot(context, padding)
            val searchPageItems = PageCustomizeSettingsItemsBuilder.searchPageCustomizeItems(
                prefs = prefs,
                texts = texts,
                isFeatureVisible = settingsSession::isFeatureVisible,
            )
            val rowsByKey = createRowsByKey(context, prefs, padding, searchPageItems)
            SettingsDialogLayout.addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = SettingsDialogLayout.createCustomHideWidgetSectionTitle(context, padding),
                rows = searchPageItems.visibleRows(rowsByKey),
            )

            val switchesByKey = collectSwitchesByKey(rowsByKey)
            if (switchesByKey.values.any { it == null }) {
                XposedCompat.logW("$LOG_TAG showSearchPage failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.SEARCH_PAGE_CUSTOMIZE_DIALOG_TITLE)
                .setView(SettingsDialogLayout.createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    PageCustomizeSettingsItemsBuilder.putSearchPageCustomizeValues(
                        editor = prefs.edit(),
                        isFeatureVisible = settingsSession::isFeatureVisible,
                        isChecked = { key -> switchesByKey[key]?.isChecked == true },
                    ).apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.SEARCH_PAGE_CUSTOMIZE_SAVED),
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
            }
            SettingsDialogWindows.showStableSubDialog(dialog, density, LOG_TAG)
        } catch (t: Throwable) {
            XposedCompat.logW("$LOG_TAG showSearchPage failed: ${t.message}")
        }
    }

    fun showSharePage(
        context: Context,
        prefs: SharedPreferences,
        settingsSession: SettingsRuntimeSession,
        texts: SettingsTextResolver,
    ) {
        try {
            if (!settingsSession.isFeatureVisible(SettingsUserState.KEY_SHARE_PAGE_CUSTOMIZE)) {
                XposedCompat.logW("$LOG_TAG showSharePage skipped: unsupported host")
                return
            }
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val root = createDialogRoot(context, padding)
            val sharePageItems = PageCustomizeSettingsItemsBuilder.sharePageCustomizeItems(
                prefs = prefs,
                texts = texts,
                isFeatureVisible = settingsSession::isFeatureVisible,
            )
            val rowsByKey = createRowsByKey(context, prefs, padding, sharePageItems)
            SettingsDialogLayout.addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = SettingsDialogLayout.createCustomHideWidgetSectionTitle(context, padding),
                rows = sharePageItems.visibleRows(rowsByKey),
            )

            val switchesByKey = collectSwitchesByKey(rowsByKey)
            if (switchesByKey.values.any { it == null }) {
                XposedCompat.logW("$LOG_TAG showSharePage failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.SHARE_PAGE_CUSTOMIZE_DIALOG_TITLE)
                .setView(SettingsDialogLayout.createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    PageCustomizeSettingsItemsBuilder.putSharePageCustomizeValues(
                        editor = prefs.edit(),
                        isFeatureVisible = settingsSession::isFeatureVisible,
                        isChecked = { key -> switchesByKey[key]?.isChecked == true },
                    ).apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.SHARE_PAGE_CUSTOMIZE_SAVED),
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
            }
            SettingsDialogWindows.showStableSubDialog(dialog, density, LOG_TAG)
        } catch (t: Throwable) {
            XposedCompat.logW("$LOG_TAG showSharePage failed: ${t.message}")
        }
    }

    fun showHome(
        context: Context,
        prefs: SharedPreferences,
        settingsSession: SettingsRuntimeSession,
        texts: SettingsTextResolver,
    ) {
        try {
            if (!settingsSession.isFeatureVisible(SettingsUserState.KEY_HOME_CUSTOMIZE)) {
                XposedCompat.logW("$LOG_TAG showHome skipped: unsupported host")
                return
            }
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val root = createDialogRoot(context, padding)
            val homeItems = PageCustomizeSettingsItemsBuilder.homeCustomizeItems(
                prefs = prefs,
                texts = texts,
                isFeatureVisible = settingsSession::isFeatureVisible,
            )
            val rowsByKey = createRowsByKey(context, prefs, padding, homeItems)

            fun visibleHomeRows(section: HomeCustomizeSettingsSection): List<View> {
                return PageCustomizeSettingsItemsBuilder.homeCustomizeItemsIn(section, homeItems)
                    .visibleRows(rowsByKey)
            }

            SettingsDialogLayout.addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = SettingsDialogLayout.createCustomHideWidgetSectionTitle(context, padding),
                rows = visibleHomeRows(HomeCustomizeSettingsSection.TOP_WIDGET),
                addDividerBefore = root.childCount > 0,
            )
            SettingsDialogLayout.addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = SettingsDialogLayout.createCustomHideSectionSectionTitle(context, padding),
                rows = visibleHomeRows(HomeCustomizeSettingsSection.CONTENT_SECTION),
                addDividerBefore = root.childCount > 0,
            )

            val switchesByKey = collectSwitchesByKey(rowsByKey)
            if (switchesByKey.values.any { it == null }) {
                XposedCompat.logW("$LOG_TAG showHome failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.HOME_CUSTOMIZE_DIALOG_TITLE)
                .setView(SettingsDialogLayout.createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    PageCustomizeSettingsItemsBuilder.putHomeCustomizeValues(
                        editor = prefs.edit(),
                        isFeatureVisible = settingsSession::isFeatureVisible,
                        isChecked = { key -> switchesByKey[key]?.isChecked == true },
                    ).apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.HOME_CUSTOMIZE_SAVED),
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
            }
            SettingsDialogWindows.showStableSubDialog(dialog, density, LOG_TAG)
        } catch (t: Throwable) {
            XposedCompat.logW("$LOG_TAG showHome failed: ${t.message}")
        }
    }

    fun showMyPage(
        context: Context,
        prefs: SharedPreferences,
        settingsSession: SettingsRuntimeSession,
        texts: SettingsTextResolver,
    ) {
        try {
            if (!settingsSession.isFeatureVisible(SettingsUserState.KEY_MY_PAGE_CUSTOMIZE)) {
                XposedCompat.logW("$LOG_TAG showMyPage skipped: unsupported host")
                return
            }
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val root = createDialogRoot(context, padding)
            val myPageItems = PageCustomizeSettingsItemsBuilder.myPageCustomizeItems(
                prefs = prefs,
                texts = texts,
                isFeatureVisible = settingsSession::isFeatureVisible,
            )
            val rowsByKey = createRowsByKey(context, prefs, padding, myPageItems)

            fun visibleMyPageRows(section: MyPageCustomizeSettingsSection): List<View> {
                return PageCustomizeSettingsItemsBuilder.myPageCustomizeItemsIn(section, myPageItems)
                    .visibleRows(rowsByKey)
            }

            SettingsDialogLayout.addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = SettingsDialogLayout.createCustomHideWidgetSectionTitle(context, padding),
                rows = visibleMyPageRows(MyPageCustomizeSettingsSection.WIDGET),
            )
            SettingsDialogLayout.addTitledSection(
                root = root,
                context = context,
                padding = padding,
                titleView = SettingsDialogLayout.createCustomHideTextWidgetSectionTitle(context, padding),
                rows = visibleMyPageRows(MyPageCustomizeSettingsSection.TEXT_WIDGET),
                addDividerBefore = root.childCount > 0,
            )

            val switchesByKey = collectSwitchesByKey(rowsByKey)
            if (switchesByKey.values.any { it == null }) {
                XposedCompat.logW("$LOG_TAG showMyPage failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, SettingsDialogWindows.themeFor(context))
                .setTitle(UiText.Settings.MY_PAGE_CUSTOMIZE_DIALOG_TITLE)
                .setView(SettingsDialogLayout.createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    PageCustomizeSettingsItemsBuilder.putMyPageCustomizeValues(
                        editor = prefs.edit(),
                        isFeatureVisible = settingsSession::isFeatureVisible,
                        isChecked = { key -> switchesByKey[key]?.isChecked == true },
                    ).apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.MY_PAGE_CUSTOMIZE_SAVED),
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
            }
            SettingsDialogWindows.showStableSubDialog(dialog, density, LOG_TAG)
        } catch (t: Throwable) {
            XposedCompat.logW("$LOG_TAG showMyPage failed: ${t.message}")
        }
    }

    private fun createDialogRoot(context: Context, padding: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun createRowsByKey(
        context: Context,
        prefs: SharedPreferences,
        padding: Int,
        items: List<KeyedSwitchItem>,
    ): LinkedHashMap<String, View> {
        val rowsByKey = linkedMapOf<String, View>()
        for (entry in items) {
            val item = entry.item
            rowsByKey[entry.key] = SettingsSwitchRows.create(
                context = context,
                prefs = prefs,
                label = item.label,
                description = item.description,
                prefKey = item.prefKey,
                padding = padding,
                enabled = item.supported,
                defaultValue = item.defaultValue,
                actionIcon = item.actionIcon,
                onActionClick = item.onActionClick,
                linkedPrefKeys = item.linkedPrefKeys,
                showSwitch = item.showSwitch,
            )
        }
        return rowsByKey
    }

    private fun collectSwitchesByKey(rowsByKey: Map<String, View>): LinkedHashMap<String, Switch?> {
        val switchesByKey = linkedMapOf<String, Switch?>()
        for ((key, row) in rowsByKey) {
            switchesByKey[key] = SettingsSwitchRows.findSwitchView(row)
        }
        return switchesByKey
    }

    private fun List<KeyedSwitchItem>.visibleRows(rowsByKey: Map<String, View>): List<View> {
        return filter { it.item.visible }
            .mapNotNull { rowsByKey[it.key] }
    }

}
