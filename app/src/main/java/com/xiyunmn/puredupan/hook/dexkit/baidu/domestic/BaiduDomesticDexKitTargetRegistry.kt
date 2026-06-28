package com.xiyunmn.puredupan.hook.dexkit.baidu.domestic

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.dexkit.DexKitHostContext
import com.xiyunmn.puredupan.hook.dexkit.DexKitTargetDescriptor
import com.xiyunmn.puredupan.hook.dexkit.DexKitTargetRegistry
import com.xiyunmn.puredupan.hook.dexkit.DexKitWarmUpTask
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticFloatViewStartupDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticThumbnailOperatorDexKitResolver

internal object BaiduDomesticDexKitTargetRegistry : DexKitTargetRegistry {
    override val descriptors = listOf(
        DexKitTargetDescriptor(
            id = DomesticThumbnailOperatorDexKitResolver.CLIENT_COMPUTE_INIT_CACHE_ID,
            target = "domestic client compute init method",
            feature = "thumbnail operator service block",
        ),
        DexKitTargetDescriptor(
            id = DomesticThumbnailOperatorDexKitResolver.THUMBNAIL_ADD_JOB_CACHE_ID,
            target = "domestic thumbnail add-job method",
            feature = "thumbnail operator service block",
        ),
        DexKitTargetDescriptor(
            id = DomesticFloatViewStartupDexKitResolver.CACHE_ID,
            target = "domestic float-view startup audio method",
            feature = "audio circle autostart block",
        ),
    )

    override fun buildTasks(host: DexKitHostContext, classLoader: ClassLoader): List<DexKitWarmUpTask> {
        val tasks = mutableListOf<DexKitWarmUpTask>()
        if (host.isFeatureAvailable(FeatureKeys.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE)) {
            tasks += DexKitWarmUpTask(DomesticThumbnailOperatorDexKitResolver.CLIENT_COMPUTE_INIT_CACHE_ID) {
                DomesticThumbnailOperatorDexKitResolver.resolveClientComputeInit(classLoader) != null
            }
            tasks += DexKitWarmUpTask(DomesticThumbnailOperatorDexKitResolver.THUMBNAIL_ADD_JOB_CACHE_ID) {
                DomesticThumbnailOperatorDexKitResolver.resolveThumbnailAddJob(classLoader) != null
            }
        }
        if (host.isFeatureAvailable(FeatureKeys.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART)) {
            tasks += DexKitWarmUpTask(DomesticFloatViewStartupDexKitResolver.CACHE_ID) {
                DomesticFloatViewStartupDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        return tasks
    }
}
