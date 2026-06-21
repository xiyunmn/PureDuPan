package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiText

internal data class FilePageCustomizeSwitchSpec(
    val key: String,
    val label: String,
    val description: String,
)

internal object FilePageCustomizeSettingsRegistry {
    val specs: List<FilePageCustomizeSwitchSpec> = listOf(
        FilePageCustomizeSwitchSpec(
            SettingsUserState.KEY_HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP,
            UiText.Settings.HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP_LABEL,
            UiText.Settings.HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP_DESC,
        ),
    )
}
