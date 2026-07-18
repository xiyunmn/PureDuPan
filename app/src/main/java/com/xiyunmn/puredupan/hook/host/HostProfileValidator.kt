package com.xiyunmn.puredupan.hook.host

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys

internal object HostProfileValidator {
    fun validate(profiles: List<HostProfile>) {
        requireUnique(values = profiles.map { it.id }, label = "host id")
        requireUnique(values = profiles.map { it.packageName }, label = "host package")
        profiles.forEach(::validateProfile)
    }

    private fun validateProfile(profile: HostProfile) {
        require(profile.mainProcessName in profile.handledProcessNames) {
            "Host ${profile.id} main process must be handled: ${profile.mainProcessName}"
        }
        require(profile.attachHookProcessNames.all { it in profile.handledProcessNames }) {
            "Host ${profile.id} attach hook process must be handled"
        }
        require(profile.pushServiceProcessNames.all { it in profile.handledProcessNames }) {
            "Host ${profile.id} push service process must be handled"
        }

        val featureKeys = profile.capabilities.features.availableKeys
        val hookCapabilities = profile.capabilities.hooks
        if (featureKeys.isNotEmpty()) {
            require(!hookCapabilities.catalogId.isNullOrBlank()) {
                "Host ${profile.id} with available features must declare a hook catalog id"
            }
        }
        hookCapabilities.catalogId?.let { catalogId ->
            require(catalogId.isNotBlank()) {
                "Host ${profile.id} hook catalog id must not be blank"
            }
        }
        profile.capabilities.settings.primarySplashAdFeatureKey?.let { featureKey ->
            require(featureKey in featureKeys) {
                "Host ${profile.id} primary splash feature must be available: $featureKey"
            }
        }

        val uiHookPoints = profile.capabilities.uiHookPoints
        requireOptionalClassName(profile, uiHookPoints.mainActivityClassName, "main activity")
        requireOptionalClassName(profile, uiHookPoints.homeActivityClassName, "home activity")
        requireOptionalClassName(profile, uiHookPoints.aboutMeActivityClassName, "about me activity")
        requireOptionalClassName(profile, uiHookPoints.newAboutMeActivityClassName, "new about me activity")
        requireOptionalClassName(
            profile,
            uiHookPoints.mainActivityPresenterClassName,
            "main activity presenter",
        )
        requireOptionalClassName(profile, uiHookPoints.newHomeFabFragmentClassName, "new home fab fragment")
        requireOptionalClassName(profile, uiHookPoints.popupResponseClassName, "popup response")
        requireOptionalClassName(profile, uiHookPoints.skinConfigClassName, "skin config")
        requireHomeCustomizeHookPoints(profile)
        requireOptionalClassNames(
            profile = profile,
            classNames = uiHookPoints.settingsImageResultHostActivityClassNames,
            label = "settings image result host activity",
        )
        requireFeatureHookPoints(profile)
        requireDexKitCapabilities(profile, featureKeys)
    }

    private fun requireDexKitCapabilities(profile: HostProfile, featureKeys: Set<String>) {
        val dexKitCapabilities = profile.capabilities.dexKit
        require(dexKitCapabilities.stableActivityClassNames.none { it.isBlank() }) {
            "Host ${profile.id} stable activity class names must not be blank"
        }
        requireUnique(
            values = dexKitCapabilities.stableActivityClassNames,
            label = "stable activity class for host ${profile.id}",
        )
        dexKitCapabilities.targetRegistryId?.let { registryId ->
            require(registryId.isNotBlank()) {
                "Host ${profile.id} DexKit registry id must not be blank"
            }
            require(FeatureKeys.KEY_DEXKIT_STATUS in featureKeys) {
                "Host ${profile.id} DexKit registry requires DexKit status feature key"
            }
            require(dexKitCapabilities.stableActivityClassNames.isNotEmpty()) {
                "Host ${profile.id} DexKit registry requires stable activity signals"
            }
        }
        if (dexKitCapabilities.showStatusInSettings) {
            require(!dexKitCapabilities.targetRegistryId.isNullOrBlank()) {
                "Host ${profile.id} DexKit status requires DexKit registry"
            }
        }
    }

    private fun requireUnique(values: List<String>, label: String) {
        val duplicates = values
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicates.isEmpty()) {
            "Duplicate $label registration: ${duplicates.joinToString()}"
        }
    }

    private fun requireOptionalClassName(profile: HostProfile, className: String?, label: String) {
        require(className == null || className.isNotBlank()) {
            "Host ${profile.id} $label class name must not be blank"
        }
    }

    private fun requireOptionalClassNames(profile: HostProfile, classNames: List<String>, label: String) {
        requireOptionalNames(profile, classNames, label)
    }

    private fun requireOptionalNames(profile: HostProfile, values: List<String>, label: String) {
        require(values.none { it.isBlank() }) {
            "Host ${profile.id} $label names must not be blank"
        }
        requireUnique(
            values = values,
            label = "$label for host ${profile.id}",
        )
    }

    private fun requireOptionalIdNames(profile: HostProfile, idNames: List<String>, label: String) {
        require(idNames.none { it.isBlank() }) {
            "Host ${profile.id} $label id names must not be blank"
        }
        requireUnique(
            values = idNames,
            label = "$label ids for host ${profile.id}",
        )
    }

    private fun requireHomeCustomizeHookPoints(profile: HostProfile) {
        val points = profile.capabilities.uiHookPoints.homeCustomize
        requireOptionalClassName(profile, points.searchboxFragmentClassName, "home searchbox fragment")
        requireOptionalClassNames(profile, points.searchTextFragmentClassNames, "home search text fragment")
        requireOptionalClassNames(profile, points.homeRootFragmentClassNames, "home root fragment")
        requireOptionalClassNames(profile, points.feedFragmentClassNames, "home feed fragment")
        requireOptionalClassNames(profile, points.toolbarFragmentClassNames, "home toolbar fragment")
        requireOptionalIdNames(profile, points.toolbarViewIdNames, "home toolbar view")
        requireOptionalClassName(profile, points.storyCardRenderContextClassName, "home story card render context")
        require(points.storyCardRenderMethodName == null || points.storyCardRenderMethodName.isNotBlank()) {
            "Host ${profile.id} home story card render method name must not be blank"
        }
        require(
            points.storyCardRenderContextClassName.isNullOrBlank() ==
                points.storyCardRenderMethodName.isNullOrBlank(),
        ) {
            "Host ${profile.id} home story card render hook point must declare class and method together"
        }
        require(points.feedRecentCardRenderMethodName == null || points.feedRecentCardRenderMethodName.isNotBlank()) {
            "Host ${profile.id} home recent card render method name must not be blank"
        }
        require(points.feedSaveCardRenderMethodName == null || points.feedSaveCardRenderMethodName.isNotBlank()) {
            "Host ${profile.id} home save card render method name must not be blank"
        }
        require(points.feedStoryCardRenderMethodName == null || points.feedStoryCardRenderMethodName.isNotBlank()) {
            "Host ${profile.id} home feed story card render method name must not be blank"
        }
        requireOptionalClassNames(profile, points.saveCardViewModelClassNames, "home save card view model")
        requireOptionalNames(profile, points.saveCardNoArgBlockedMethodNames, "home save no-arg block method")
        requireOptionalNames(profile, points.saveCardSetListMethodNames, "home save set list method")
        requireOptionalNames(profile, points.saveCardSetRecommendMethodNames, "home save set recommend method")
        requireOptionalNames(profile, points.saveCardRedPotMethodNames, "home save red pot method")
        requireOptionalClassNames(profile, points.recentCardDataUseCaseClassNames, "home recent card data use case")
        requireOptionalClassName(
            profile,
            points.netdiskContextCompanionClassName,
            "netdisk context companion",
        )
        require(points.newHomeBannerCardViewMethodName == null || points.newHomeBannerCardViewMethodName.isNotBlank()) {
            "Host ${profile.id} new home banner card view method name must not be blank"
        }
        require(
            points.netdiskContextCompanionClassName.isNullOrBlank() ==
                points.newHomeBannerCardViewMethodName.isNullOrBlank(),
        ) {
            "Host ${profile.id} new home banner render hook point must declare class and method together"
        }
        requireOptionalClassName(
            profile,
            points.home25aiContextCompanionClassName,
            "home25ai context companion",
        )
        require(points.loadHomeBannerMethodName == null || points.loadHomeBannerMethodName.isNotBlank()) {
            "Host ${profile.id} load home banner method name must not be blank"
        }
        require(
            points.home25aiContextCompanionClassName.isNullOrBlank() ==
                points.loadHomeBannerMethodName.isNullOrBlank(),
        ) {
            "Host ${profile.id} home banner preload hook point must declare class and method together"
        }
    }

    private fun requireFeatureHookPoints(profile: HostProfile) {
        val featureKeys = profile.capabilities.features.availableKeys
        val uiHookPoints = profile.capabilities.uiHookPoints

        if (featureKeys.any(::requiresMainActivityHookPoint)) {
            requireRequiredClassName(profile, uiHookPoints.mainActivityClassName, "main activity")
        }
        if (FeatureKeys.KEY_BLOCK_BOTTOM_BADGE in featureKeys) {
            requireRequiredClassName(
                profile,
                uiHookPoints.mainActivityPresenterClassName,
                "main activity presenter",
            )
        }
        if (featureKeys.any(::requiresAboutMeActivityHookPoint)) {
            requireRequiredClassName(profile, uiHookPoints.aboutMeActivityClassName, "about me activity")
        }
        if (FeatureKeys.KEY_MEMBER_CARD_CUSTOMIZE in featureKeys) {
            requireRequiredClassNames(
                profile,
                uiHookPoints.settingsImageResultHostActivityClassNames,
                "settings image result host activity",
            )
        }
        if (FeatureKeys.KEY_HOME_CUSTOMIZE in featureKeys) {
            requireRequiredHomeCustomizeHookPoints(profile)
        }
        if (FeatureKeys.KEY_REMOVE_HOME_FAB in featureKeys) {
            requireRequiredClassName(profile, uiHookPoints.newHomeFabFragmentClassName, "new home fab fragment")
            requireRequiredClassName(profile, uiHookPoints.popupResponseClassName, "popup response")
        }
        if (FeatureKeys.KEY_ACCELERATE_INTL_SPLASH_STARTUP in featureKeys) {
            requireRequiredClassName(profile, uiHookPoints.skinConfigClassName, "skin config")
        }
    }

    private fun requiresMainActivityHookPoint(featureKey: String): Boolean =
        featureKey in setOf(
            FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL,
            FeatureKeys.KEY_CUSTOM_BOTTOM_BAR,
            FeatureKeys.KEY_BLOCK_BOTTOM_BADGE,
            FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE,
            FeatureKeys.KEY_ACCELERATE_INTL_SPLASH_STARTUP,
            FeatureKeys.KEY_DELAY_INTL_FEED_PRELOAD,
            FeatureKeys.KEY_DELAY_INTL_TASK_SCORE_REFRESH,
            FeatureKeys.KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET,
            FeatureKeys.KEY_DELAY_INTL_FLOAT_VIEW_STARTUP,
            FeatureKeys.KEY_BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW,
        )

    private fun requiresAboutMeActivityHookPoint(featureKey: String): Boolean =
        featureKey in setOf(
            FeatureKeys.KEY_MY_PAGE_CUSTOMIZE,
            FeatureKeys.KEY_HIDE_RENEW_BUTTON,
            FeatureKeys.KEY_MEMBER_CARD_CUSTOMIZE,
        )

    private fun requireRequiredHomeCustomizeHookPoints(profile: HostProfile) {
        val featureKeys = profile.capabilities.features.availableKeys
        val points = profile.capabilities.uiHookPoints.homeCustomize

        val needsSearchbox = featureKeys.any {
            it in setOf(
                FeatureKeys.KEY_HIDE_HOME_TOP_PROMOTION,
                FeatureKeys.KEY_HIDE_HOME_SEARCH_AIGC_ICON,
            )
        }
        if (needsSearchbox) {
            requireRequiredClassName(profile, points.searchboxFragmentClassName, "home searchbox fragment")
        }
        if (FeatureKeys.KEY_HIDE_HOME_SEARCH_PLACEHOLDER in featureKeys) {
            requireRequiredClassNames(profile, points.searchTextFragmentClassNames, "home search text fragment")
        }

        if (FeatureKeys.KEY_HIDE_HOME_FEED_TIP in featureKeys) {
            requireRequiredClassNames(profile, points.feedFragmentClassNames, "home feed fragment")
        }
        if (FeatureKeys.KEY_HIDE_HOME_TOOLBAR in featureKeys) {
            requireRequiredClassNames(profile, points.homeRootFragmentClassNames, "home root fragment")
            requireRequiredClassNames(profile, points.toolbarFragmentClassNames, "home toolbar fragment")
            requireRequiredIdNames(profile, points.toolbarViewIdNames, "home toolbar view")
        }
        if (FeatureKeys.KEY_HIDE_HOME_MEMORIES_SECTION in featureKeys) {
            requireRequiredClassName(profile, points.storyCardRenderContextClassName, "home story card render context")
            require(!points.storyCardRenderMethodName.isNullOrBlank()) {
                "Host ${profile.id} requires home story card render method name"
            }
            requireRequiredClassNames(profile, points.feedFragmentClassNames, "home feed fragment")
            require(!points.feedStoryCardRenderMethodName.isNullOrBlank()) {
                "Host ${profile.id} requires home feed story card render method name"
            }
        }
        if (FeatureKeys.KEY_HIDE_HOME_SAVE_SECTION in featureKeys) {
            requireRequiredClassNames(profile, points.feedFragmentClassNames, "home feed fragment")
            require(!points.feedSaveCardRenderMethodName.isNullOrBlank()) {
                "Host ${profile.id} requires home save card render method name"
            }
            requireRequiredClassNames(profile, points.saveCardViewModelClassNames, "home save card view model")
            requireRequiredNames(profile, points.saveCardNoArgBlockedMethodNames, "home save no-arg block method")
            requireRequiredNames(profile, points.saveCardSetListMethodNames, "home save set list method")
            requireRequiredNames(
                profile,
                points.saveCardSetRecommendMethodNames,
                "home save set recommend method",
            )
            requireRequiredNames(profile, points.saveCardRedPotMethodNames, "home save red pot method")
        }
        if (FeatureKeys.KEY_HIDE_HOME_RECENT_SECTION in featureKeys) {
            requireRequiredClassNames(profile, points.feedFragmentClassNames, "home feed fragment")
            require(!points.feedRecentCardRenderMethodName.isNullOrBlank()) {
                "Host ${profile.id} requires home recent card render method name"
            }
            requireRequiredClassNames(profile, points.recentCardDataUseCaseClassNames, "home recent card data use case")
        }
        if (FeatureKeys.KEY_HOME_RECENT_ITEM_LIMIT in featureKeys) {
            requireRequiredClassNames(
                profile,
                points.recentCardViewModelClassNames,
                "home recent card view model",
            )
        }
        if (FeatureKeys.KEY_HOME_SAVE_VERTICAL_LAYOUT in featureKeys) {
            requireRequiredClassNames(profile, points.saveCardViewClassNames, "home save card view")
        }
        if (FeatureKeys.KEY_HIDE_HOME_BANNER in featureKeys) {
            requireRequiredClassName(profile, points.netdiskContextCompanionClassName, "netdisk context companion")
            require(!points.newHomeBannerCardViewMethodName.isNullOrBlank()) {
                "Host ${profile.id} requires new home banner card view method name"
            }
        }
        if (FeatureKeys.KEY_HIDE_HOME_TOP_PROMOTION in featureKeys) {
            requireRequiredClassName(profile, points.home25aiContextCompanionClassName, "home25ai context companion")
            require(!points.loadHomeBannerMethodName.isNullOrBlank()) {
                "Host ${profile.id} requires load home banner method name"
            }
        }
    }

    private fun requireRequiredClassName(profile: HostProfile, className: String?, label: String) {
        require(!className.isNullOrBlank()) {
            "Host ${profile.id} requires $label class name"
        }
    }

    private fun requireRequiredClassNames(profile: HostProfile, classNames: List<String>, label: String) {
        require(classNames.isNotEmpty()) {
            "Host ${profile.id} requires $label class names"
        }
        requireOptionalClassNames(profile, classNames, label)
    }

    private fun requireRequiredNames(profile: HostProfile, values: List<String>, label: String) {
        require(values.isNotEmpty()) {
            "Host ${profile.id} requires $label names"
        }
        requireOptionalNames(profile, values, label)
    }

    private fun requireRequiredIdNames(profile: HostProfile, idNames: List<String>, label: String) {
        require(idNames.isNotEmpty()) {
            "Host ${profile.id} requires $label id names"
        }
        requireOptionalIdNames(profile, idNames, label)
    }
}
