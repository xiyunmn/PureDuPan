package com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColdStartAdPolicyTest {
    @Test
    fun readyBeforeLaunchUsesNativeNoAdMode() {
        val policy = ColdStartAdPolicy()
        policy.markReady()
        assertTrue(policy.select(enabled = true))
    }

    @Test
    fun warmUpDuringLaunchCannotChangeRoutingMode() {
        val policy = ColdStartAdPolicy()
        assertFalse(policy.select(enabled = true))
        policy.markReady()
        assertFalse(policy.select(enabled = true))
    }

    @Test
    fun disabledAtLaunchKeepsNativeRoutingAfterSettingsChange() {
        val policy = ColdStartAdPolicy()
        policy.markReady()
        assertFalse(policy.select(enabled = false))
        assertFalse(policy.select(enabled = true))
    }

    @Test
    fun activeLaunchFinishesInSameModeUntilProcessRestart() {
        val policy = ColdStartAdPolicy()
        policy.markReady()
        assertTrue(policy.select(enabled = true))
        assertTrue(policy.select(enabled = false))
        val restarted = ColdStartAdPolicy()
        restarted.markReady()
        assertFalse(restarted.select(enabled = false))
    }
}
