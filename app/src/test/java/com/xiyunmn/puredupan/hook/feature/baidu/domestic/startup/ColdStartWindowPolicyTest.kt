package com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColdStartWindowPolicyTest {
    @Test
    fun launcherHandoffPreparesOnlyTheFirstMainInTheSameTask() {
        val policy = ColdStartWindowPolicy()
        policy.recordNavigate(7, launcher = true, restored = false, nativeNoAd = true)
        assertTrue(policy.consumeMain(7, restored = false, nativeNoAd = true))
        assertFalse(policy.consumeMain(7, restored = false, nativeNoAd = true))
        policy.recordNavigate(7, launcher = true, restored = false, nativeNoAd = true)
        assertFalse(policy.consumeMain(7, restored = false, nativeNoAd = true))
    }

    @Test
    fun externalEntryCannotArmALaterLauncher() {
        val policy = ColdStartWindowPolicy()
        policy.recordNavigate(7, launcher = false, restored = false, nativeNoAd = true)
        policy.recordNavigate(7, launcher = true, restored = false, nativeNoAd = true)
        assertFalse(policy.consumeMain(7, restored = false, nativeNoAd = true))
    }

    @Test
    fun directMainAndRestoredActivitiesKeepHostWindowDefaults() {
        assertFalse(ColdStartWindowPolicy().consumeMain(7, restored = false, nativeNoAd = true))
        val restoredNavigate = ColdStartWindowPolicy()
        restoredNavigate.recordNavigate(7, launcher = true, restored = true, nativeNoAd = true)
        assertFalse(restoredNavigate.consumeMain(7, restored = false, nativeNoAd = true))
        val restoredMain = ColdStartWindowPolicy()
        restoredMain.recordNavigate(7, launcher = true, restored = false, nativeNoAd = true)
        assertFalse(restoredMain.consumeMain(7, restored = true, nativeNoAd = true))
    }

    @Test
    fun anotherTaskConsumesTheHandoffWithoutApplyingIt() {
        val policy = ColdStartWindowPolicy()
        policy.recordNavigate(7, launcher = true, restored = false, nativeNoAd = true)
        assertFalse(policy.consumeMain(8, restored = false, nativeNoAd = true))
        assertFalse(policy.consumeMain(7, restored = false, nativeNoAd = true))
        val unknownTask = ColdStartWindowPolicy()
        unknownTask.recordNavigate(-1, launcher = true, restored = false, nativeNoAd = true)
        assertFalse(unknownTask.consumeMain(-1, restored = false, nativeNoAd = true))
    }

    @Test
    fun lateResolutionCannotEnableWindowChangesDuringNativeLaunch() {
        val policy = ColdStartWindowPolicy()
        policy.recordNavigate(7, launcher = true, restored = false, nativeNoAd = false)
        assertFalse(policy.consumeMain(7, restored = false, nativeNoAd = true))
        val disabled = ColdStartWindowPolicy()
        disabled.recordNavigate(7, launcher = true, restored = false, nativeNoAd = true)
        assertFalse(disabled.consumeMain(7, restored = false, nativeNoAd = false))
    }
}
