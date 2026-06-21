package com.xiyunmn.puredupan.hook.host

internal data class HomeCustomizeHookPoints(
    val searchboxFragmentClassName: String? = null,
    val homeRootFragmentClassNames: List<String> = emptyList(),
    val feedFragmentClassNames: List<String> = emptyList(),
    val toolbarViewIdNames: List<String> = emptyList(),
    val storyCardViewClassNames: List<String> = emptyList(),
    val saveCardViewClassNames: List<String> = emptyList(),
    val recentCardViewClassNames: List<String> = emptyList(),
    val home25aiContextCompanionClassName: String? = null,
    val loadHomeBannerMethodName: String? = null,
)
