package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduFilePageHookPoints {
    const val FILE_LIST_TOOLBAR_HEADER =
        "com.baidu.netdisk.allfiles.listfragment.extraview.header.FileListToolBarHeader"
    const val FILE_LIST_TOOLBAR_HEADER_VIEW =
        "com.baidu.netdisk.allfiles.listfragment.extraview.header.FileListToolBarHeaderView"
    const val FILTER_TYPE_TAG_ADAPTER =
        "com.baidu.netdisk.allfiles.listfragment.extraview.header.FilterTypeTagSelectAdapter"
    const val RECYCLER_VIEW_ADAPTER = "androidx.recyclerview.widget.RecyclerView\$Adapter"

    /**
     * 旧版文件页底部安全提示渲染入口（明文，跨版本稳定）。
     *
     * 国内版/三星版 13.27.8、国际版 13.11.9 均保留明文
     * `MyNetdiskFragment.initSafetyBottomView(Context)`，方法体一致：
     * 无条件 `inflate(safety_ability_layout) + mListView.addFooterView(...)`。这是旧
     * ListView 文件页兼容入口；国际版实际主文件页已切换到下方 RecyclerView v2 路径。
     * 分类页依赖初始化后的 mBottomSafety；隐藏提示时保留对象、footer 及其原生尺寸。
     */
    const val MY_NETDISK_FRAGMENT =
        "com.baidu.netdisk.ui.cloudfile.MyNetdiskFragment"

    const val INIT_SAFETY_BOTTOM_VIEW_METHOD = "initSafetyBottomView"
    const val BOTTOM_SAFETY_FIELD = "mBottomSafety"

    /** RecyclerView v2 文件页直接添加安全提示 footer，不经过旧 MyNetdiskFragment 入口。 */
    const val FILE_LIST_RECYCLER_VIEW =
        "com.baidu.netdisk.filelist.view.FileListRecyclerView"

    const val ADD_FOOTER_VIEW_METHOD = "addFooterView"
    const val SAFETY_INSTRUCTIONS_VIEW = "com.baidu.netdisk.ui.SafetyInstructionsView"
    const val SAFETY_ABILITY_VIEW_ID = "safe_ability_layout"
}
