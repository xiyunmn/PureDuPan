package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduFilePageHookPoints {
    const val FILE_LIST_TOOLBAR_HEADER =
        "com.baidu.netdisk.allfiles.listfragment.extraview.header.FileListToolBarHeader"
    const val FILE_LIST_TOOLBAR_HEADER_VIEW =
        "com.baidu.netdisk.allfiles.listfragment.extraview.header.FileListToolBarHeaderView"
    const val FILTER_TYPE_TAG_ADAPTER =
        "com.baidu.netdisk.allfiles.listfragment.extraview.header.FilterTypeTagSelectAdapter"
    const val RECYCLER_VIEW_ADAPTER = "androidx.recyclerview.widget.RecyclerView\$Adapter"

    /** 旧 ListView 文件页的安全提示初始化入口。分类页依赖其对象，隐藏时需保留 footer 及原始尺寸。 */
    const val MY_NETDISK_FRAGMENT =
        "com.baidu.netdisk.ui.cloudfile.MyNetdiskFragment"

    const val INIT_SAFETY_BOTTOM_VIEW_METHOD = "initSafetyBottomView"
    const val BOTTOM_SAFETY_FIELD = "mBottomSafety"

    /** RecyclerView 文件页通过独立入口添加安全提示 footer。 */
    const val FILE_LIST_RECYCLER_VIEW =
        "com.baidu.netdisk.filelist.view.FileListRecyclerView"

    const val ADD_FOOTER_VIEW_METHOD = "addFooterView"
    const val SAFETY_INSTRUCTIONS_VIEW = "com.baidu.netdisk.ui.SafetyInstructionsView"
    const val SAFETY_ABILITY_VIEW_ID = "safe_ability_layout"
}
