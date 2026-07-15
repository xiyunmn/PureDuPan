package com.xiyunmn.puredupan.hook.host

internal data class HomeCustomizeHookPoints(
    val searchboxFragmentClassName: String? = null,
    val searchTextFragmentClassNames: List<String> = emptyList(),
    val homeRootFragmentClassNames: List<String> = emptyList(),
    val feedFragmentClassNames: List<String> = emptyList(),
    val toolbarFragmentClassNames: List<String> = emptyList(),
    val toolbarViewIdNames: List<String> = emptyList(),
    val storyCardViewClassNames: List<String> = emptyList(),
    val saveCardViewClassNames: List<String> = emptyList(),
    val recentCardDataUseCaseClassName: String? = null,
    val home25aiContextCompanionClassName: String? = null,
    val loadHomeBannerMethodName: String? = null,
)
