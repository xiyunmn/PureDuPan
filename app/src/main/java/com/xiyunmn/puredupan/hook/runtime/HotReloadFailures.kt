package com.xiyunmn.puredupan.hook.runtime

/** 汇总本次交接失败，让独立资源继续收尾。 */
internal class HotReloadFailures(private val report: (String, Throwable) -> Unit) {
    var firstFailure: Throwable? = null
        private set
    var firstStage: String? = null
        private set

    fun record(stage: String, error: Throwable) {
        if (firstFailure == null) {
            firstFailure = error
            firstStage = stage
        }
        report(stage, error)
    }

    fun run(stage: String, action: () -> Unit) {
        try {
            action()
        } catch (error: Throwable) {
            record(stage, error)
        }
    }
}
