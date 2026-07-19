package com.xiyunmn.puredupan.hook.host

internal enum class HomeSaveCardImplementation {
    DOMESTIC,
    INTL,
}

internal data class HomeCustomizeHookPoints(
    val searchboxFragmentClassName: String? = null,
    val searchTextFragmentClassNames: List<String> = emptyList(),
    val homeRootFragmentClassNames: List<String> = emptyList(),
    val feedFragmentClassNames: List<String> = emptyList(),
    val toolbarFragmentClassNames: List<String> = emptyList(),
    val toolbarViewIdNames: List<String> = emptyList(),
    val storyCardRenderContextClassName: String? = null,
    val storyCardRenderMethodName: String? = null,
    val feedRecentCardRenderMethodName: String? = null,
    val feedSaveCardRenderMethodName: String? = null,
    val feedStoryCardRenderMethodName: String? = null,
    val saveCardViewModelClassNames: List<String> = emptyList(),
    val saveCardViewClassNames: List<String> = emptyList(),
    val saveCardNoArgBlockedMethodNames: List<String> = emptyList(),
    val saveCardSetListMethodNames: List<String> = emptyList(),
    val saveCardSetRecommendMethodNames: List<String> = emptyList(),
    val saveCardRedPotMethodNames: List<String> = emptyList(),
    val saveCardImplementation: HomeSaveCardImplementation = HomeSaveCardImplementation.DOMESTIC,
    val recentCardDataUseCaseClassNames: List<String> = emptyList(),
    val recentCardViewModelClassNames: List<String> = emptyList(),
    val supportsRecentScrollRangeAdjustment: Boolean = false,
    val netdiskContextCompanionClassName: String? = null,
    val newHomeBannerCardViewMethodName: String? = null,
    val home25aiContextCompanionClassName: String? = null,
    val loadHomeBannerMethodName: String? = null,
)
