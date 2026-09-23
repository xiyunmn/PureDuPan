package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduMarketingTransferHookPoints {
    const val BUSINESS_PROVIDER =
        "com.baidu.netdisk.business.guide.component.provider.BusinessGuideProvider"
    const val SEARCH_GUIDE_METHOD = "showSearchGuideDialog"
    const val DIALOG_CALLBACK = "com.baidu.netdisk.business.guide.IDialogGuideCallback"
    const val FRAGMENT_ACTIVITY = "androidx.fragment.app.FragmentActivity"
    const val FUNCTION0 = "kotlin.jvm.functions.Function0"
    const val TRANSFER_VIEW_MODEL =
        "com.baidu.netdisk.ui.businessplatform.guide.transfer.TransferFileViewModel"
    const val TRANSFER_LIMIT_GUIDE =
        "com.baidu.netdisk.ui.businessplatform.guide.transfer.TransferFileLimitGuide"
    const val TRANSFER_CALLBACK =
        "com.baidu.netdisk.ui.businessplatform.guide.transfer.ITransferGuide"
    const val SHARE_PRESENTER = "com.baidu.netdisk.module.sharelink.ShareFileOpPresenter"
    const val SAVE_RESULT_RECEIVER = "$SHARE_PRESENTER\$SaveResultReceiver"
    const val GUIDE_CONTEXT =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_platform_business_guide.GuideContext"
    const val GUIDE_COMPANION = "$GUIDE_CONTEXT\$Companion"
    const val NO_SPACE_BEAN =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_platform_business_guide.NoSpaceGuideBean"
    const val SHOW_NO_SPACE_METHOD = "showNoSpaceGuideDialog"
    const val TRANSFER_PRIVILEGE_ENABLED = "isTransferPrivilegeGuideOpen"
    const val TRANSFER_SPACE_SCENE = "save_file_no_space_guide"
    const val DISMISS_FINISH_KEY = "on_dismiss_finish_container"
    const val FLUTTER_ROUTER = "com.baidu.netdisk.flutter.plugin.router.NetdiskRouterPluginProxy"
    const val FLUTTER_METHOD_CALL = "io.flutter.plugin.common.MethodCall"
    const val FLUTTER_RESULT = "io.flutter.plugin.common.MethodChannel\$Result"
}
