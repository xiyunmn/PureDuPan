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
}
