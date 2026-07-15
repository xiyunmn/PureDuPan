package com.xiyunmn.puredupan.hook.host

internal data class HomeCustomizeHookPoints(
    val searchboxFragmentClassName: String? = null,
    val searchTextFragmentClassNames: List<String> = emptyList(),
    val homeRootFragmentClassNames: List<String> = emptyList(),
    val feedFragmentClassNames: List<String> = emptyList(),
    val toolbarFragmentClassNames: List<String> = emptyList(),
    val toolbarViewIdNames: List<String> = emptyList(),
    val storyCardRenderContextClassName: String? = null,
    val storyCardRenderMethodName: String? = null,
    val saveCardViewModelClassName: String? = null,
    val saveCardNoArgBlockedMethodNames: List<String> = emptyList(),
    val saveCardSetListMethodNames: List<String> = emptyList(),
    val saveCardSetRecommendMethodNames: List<String> = emptyList(),
    val saveCardRedPotMethodNames: List<String> = emptyList(),
    val recentCardDataUseCaseClassName: String? = null,
    val netdiskContextCompanionClassName: String? = null,
    val newHomeBannerCardViewMethodName: String? = null,
    val home25aiContextCompanionClassName: String? = null,
    val loadHomeBannerMethodName: String? = null,
)
