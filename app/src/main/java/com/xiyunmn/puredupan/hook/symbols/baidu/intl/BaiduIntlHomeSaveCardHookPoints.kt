package com.xiyunmn.puredupan.hook.symbols.baidu.intl

internal object BaiduIntlHomeSaveCardHookPoints {
    const val ACCOUNT_UTILS = "com.baidu.netdisk.account.AccountUtils"
    const val TRANSFER_SAVED_MANAGER = "com.baidu.netdisk.logic.TransferSavedManager"
    const val HANDLABLE_MANAGER = "com.baidu.netdisk.service.HandlableManager"
    const val ANDROIDX_OBSERVER = "androidx.lifecycle.Observer"
    const val GSON = "com.google.gson.Gson"
    const val UPDATE_OBSERVER_SUFFIX = "\$updateCardInfo\$\$inlined\$observerOnlyOnce\$1"

    const val SAVE_CARD_LAYOUT = "new_fh_fragment_save"
    const val SAVE_GROUP_ID = "horizontalScrollView"
    const val SUBSCRIPTION_GROUP_ID = "linkHorizontalScrollView"
    const val CONTENT_AREA_ID = "fh_save_date_area"

    val SAVE_ROW_IDS = listOf("root_one_layout", "root_two_layout", "root_three_layout")
    val SAVE_INNER_IDS = listOf("fh_cl_one", "fh_cl_two", "fh_cl_three")
    val SUBSCRIPTION_ROW_IDS =
        listOf("link_root_one_layout", "link_root_two_layout", "link_root_three_layout")
}
