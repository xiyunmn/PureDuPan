package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduFilePageHookPoints {
    /**
     * 新文件页底部安全提示数据层入口。
     *
     * 弱混淆分支保留原类名；强混淆分支必须由 DexKit 根据 Kotlin Metadata d2
     * 的 `ShowSafetyFooterUseCase` / `realExecute` / `IFileListViewModel` 动态发现。
     */
    const val SHOW_SAFETY_FOOTER_USE_CASE =
        "com.baidu.netdisk.filelist.domain.common.ShowSafetyFooterUseCase"

    const val FILE_LIST_VIEW_MODEL_INTERFACE =
        "com.baidu.netdisk.filelist.IFileListViewModel"

    /**
     * 旧版文件页底部安全提示渲染入口（明文，跨版本稳定）。
     *
     * 国内版/三星版 13.27.8、国际版 13.11.9 均保留明文
     * `MyNetdiskFragment.initSafetyBottomView(Context)`，方法体一致：
     * 无条件 `inflate(safety_ability_layout) + mListView.addFooterView(...)`。这是旧
     * ListView 文件页兼容入口；国际版实际主文件页已切换到下方 RecyclerView v2 路径。
     */
    const val MY_NETDISK_FRAGMENT =
        "com.baidu.netdisk.ui.cloudfile.MyNetdiskFragment"

    const val INIT_SAFETY_BOTTOM_VIEW_METHOD = "initSafetyBottomView"

    /** RecyclerView v2 文件页直接添加安全提示 footer，不经过旧 MyNetdiskFragment 入口。 */
    const val FILE_LIST_RECYCLER_VIEW =
        "com.baidu.netdisk.filelist.view.FileListRecyclerView"

    const val ADD_FOOTER_VIEW_METHOD = "addFooterView"
    const val SAFETY_INSTRUCTIONS_VIEW = "com.baidu.netdisk.ui.SafetyInstructionsView"
    const val SAFETY_ABILITY_VIEW_ID = "safe_ability_layout"
}
