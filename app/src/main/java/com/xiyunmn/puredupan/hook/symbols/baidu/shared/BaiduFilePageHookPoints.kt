package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduFilePageHookPoints {
    /**
     * 新文件页底部安全提示数据层入口。
     *
     * 弱混淆分支保留原类名；国内版/三星版 13.27.8 强混淆样本中类名为
     * `kotlin.chm0`，但 Kotlin Metadata d2 仍保留
     * `ShowSafetyFooterUseCase` / `realExecute` / `IFileListViewModel`。
     * 运行时必须先校验 metadata 和方法形态，不能只信短类名。
     */
    const val SHOW_SAFETY_FOOTER_USE_CASE =
        "com.baidu.netdisk.filelist.domain.common.ShowSafetyFooterUseCase"

    const val SHOW_SAFETY_FOOTER_USE_CASE_STRONG_SAMPLE = "kotlin.chm0"

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
