package com.xiyunmn.puredupan.hook.symbols.baidu.intl

internal object BaiduIntlStoryHookPoints {
    const val DOUYIN_OPEN_API_FACTORY = "com.bytedance.sdk.open.douyin.DouYinOpenApiFactory"
    const val DOUYIN_OPEN_CONFIG = "com.bytedance.sdk.open.douyin.DouYinOpenConfig"
    const val STORY_COMPONENT_CLASS = "com.baidu.netdisk.newstory.component.c"
    const val STORY_INIT_13_11_METHOD = "___"
    const val STORY_VIDEO_PRELOADER_INIT_DESCRIPTOR =
        "Lcom/mars/united/storyvideo/preload/StoryVideoPreloaderContextKt;->b" +
            "(Landroid/app/Application;Ljava/util/concurrent/Executor;" +
            "Lcom/mars/united/storyvideo/preload/Im3u8Fetcher;ZJILjava/lang/Object;)V"
    const val STORY_UI_SERVICE_INIT_DESCRIPTOR =
        "Ls40/____;->v" +
            "(Lcom/baidu/netdisk/newstory/component/dependency/IStory;Ljava/util/concurrent/Executor;)V"

    val STORY_SEMANTIC_TOKENS = listOf(
        "initStory",
        "application",
        "initDouyinSdk",
        "BaiduNetDiskModules_Story",
    )

    val STORY_ENTRY_ACTIVITIES = listOf(
        "com.baidu.netdisk.newstory.calendar.ui.view.StorySetActivity",
        "com.baidu.netdisk.newstory.feedhome.ui.view.HomeStoryActivity",
        "com.baidu.netdisk.newstory.feedhome.ui.view.FeedHomeToolsMakeSameActivity",
        "com.baidu.netdisk.newstory.ui.view.AllStoryListActivity",
        "com.baidu.netdisk.newstory.ui.view.GenerateStyleVideoStyleLoadingActivity",
        "com.baidu.netdisk.newstory.ui.view.GenerateVideoLoadingActivity",
        "com.baidu.netdisk.newstory.ui.view.GenerateVideoPreviewActivity",
        "com.baidu.netdisk.newstory.ui.view.GenerateVideoStyleSelectionActivity",
        "com.baidu.netdisk.newstory.ui.view.MemoryStoryDetailActivity",
        "com.baidu.netdisk.newstory.ui.view.MemoryStoryMediaListActivity",
        "com.baidu.netdisk.newstory.ui.view.MemoryStorySingleMediaActivity",
        "com.baidu.netdisk.newstory.ui.view.share.MemoryStoryMediaShareActivity",
        "com.baidu.netdisk.newstory.campus.preview.ui.GraduationSeasonActivity",
        "com.baidu.netdisk.newstory.campus.share.ui.activity.GraSeasonGenerateLoadingActivity",
        "com.baidu.netdisk.newstory.campus.share.ui.activity.GraSeasonGenerateVideoShareActivity",
        "com.baidu.netdisk.newstory.campus.share.ui.activity.GraSeasonShareActivity",
    )

    val DOUYIN_ENTRY_ACTIVITIES = listOf(
        "com.baidu.netdisk.newstory.ui.view.DouYinEntryActivity",
        "com.bytedance.sdk.open.douyin.ui.DouYinWebAuthorizeActivity",
        "com.baidu.sapi2.activity.social.DouyinSSOLoginActivity",
    )
}
