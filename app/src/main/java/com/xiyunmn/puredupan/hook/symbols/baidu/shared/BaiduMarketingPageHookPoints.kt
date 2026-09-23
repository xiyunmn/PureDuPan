package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduMarketingPageHookPoints {
    const val flutterActivity = "com.baidu.netdisk.flutter.ui.FlutterBusinessActivity"
    const val searchRoute = "/netdisk/search"
    val searchPages = setOf(
        "com.baidu.netdisk.ui.cloudfile.HomeSearchActivity",
        "com.baidu.netdisk.ui.cloudfile.SearchActivity",
    )
    val mainPages = setOf(
        "com.baidu.netdisk.ui.MainActivity",
        "com.baidu.netdisk.homepage.HomeActivity",
        "com.baidu.netdisk.ui.cloudfile.MyNetdiskActivity",
        "com.baidu.netdisk.ui.cloudp2p.ShareListActivity",
        "com.baidu.netdisk.ui.aboutme.AboutMeActivity",
        "com.baidu.netdisk.ui.aboutme.NewAboutMeActivity",
    )
}
