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
     * 无条件 `inflate(safety_ability_layout) + mListView.addFooterView(...)`，
     * 不经过 [SHOW_SAFETY_FOOTER_USE_CASE] 数据层门（国际版无该 UseCase 路径，
     * 故数据层 resolver 在国际版必然失效，须走渲染入口）。`OpenNetdiskFragment`
     * 继承不 override；`mBottomSafety` 字段仅赋值从不读取，跳过方法体 NPE 安全。
     */
    const val MY_NETDISK_FRAGMENT =
        "com.baidu.netdisk.ui.cloudfile.MyNetdiskFragment"

    const val INIT_SAFETY_BOTTOM_VIEW_METHOD = "initSafetyBottomView"
}
