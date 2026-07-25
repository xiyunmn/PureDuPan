package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduBottomBarHookPoints {
    const val LOTTIE_RADIO_BUTTON = "com.baidu.netdisk.ui.lottie.LottieRadioButton"

    const val THEME_UPDATE_METHOD = "onThemeUpdate"
    const val INIT_TABS_METHOD = "initTabs"
    const val INIT_TABS_SKIN_METHOD = "initTabsSkin"
    const val REFRESH_TAB_VIEW_TEXT_METHOD = "refreshTabViewText"
    const val REFRESH_TAB_FOLDER_METHOD = "refreshTabFolder"
    const val GET_LOTTIE_ROOT_FOLDER_METHOD = "getLottieRootFolder"
    const val SHOW_HOME_TAB_METHOD = "showHomeTabAnim"
    const val SHOW_HOME_TOP_TAB_METHOD = "showHomeTabTopAnim"
    const val SHOW_FILE_TAB_METHOD = "showFileTabAnim"
    const val SHOW_SHARE_TAB_METHOD = "showShareTabAnim"
    const val SHOW_FIND_TAB_METHOD = "showFindTabAnim"
    const val SHOW_ABOUT_ME_TAB_METHOD = "showAboutMeAnim"

    const val CHILD_INDEX_FIELD = "mChildId"
    const val TAB_CONTAINER_FIELD = "mTab"
    const val TAB_ROOT_FIELD = "mTabRoot"
    const val CONTENT_VIEW_FIELD = "mContentView"
    const val SKIN_DATA_FIELD = "skinData"

    const val HOME_TAB_ID_NAME = "rb_home"
    const val FILE_TAB_ID_NAME = "rb_filelist"
    const val SHARE_TAB_ID_NAME = "rb_share"
    const val FIND_TAB_ID_NAME = "rb_findresoure"
    const val ABOUT_ME_TAB_ID_NAME = "rb_about_me"

    val DOMESTIC_HOME_FOLDED_FIELDS = listOf("lastNewFHomeTopFolded", "lastFHomeTopFolded")
    val INTL_HOME_FOLDED_FIELDS = listOf("lastFHomeTopFolded", "lastNewFHomeTopFolded")

    val DOMESTIC_THEME_REFRESH_METHODS = listOf(INIT_TABS_SKIN_METHOD)
    val INTL_THEME_REFRESH_METHODS = listOf(THEME_UPDATE_METHOD)
}
