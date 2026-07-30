package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedHistoryRetryStateTest {
    @Test
    fun retriesUseBoundedBackoff() {
        val state = SavedHistoryRetryState()

        assertEquals(500L, state.scheduleNext())
        assertTrue(state.beginScheduledAttempt())
        assertEquals(1_500L, state.markRetryableFailure())
        assertTrue(state.beginScheduledAttempt())
        assertEquals(4_000L, state.markRetryableFailure())
        assertTrue(state.beginScheduledAttempt())
        assertNull(state.markRetryableFailure())
        assertTrue(state.isExhausted())
        assertEquals(3, state.attemptCount)
    }

    @Test
    fun scheduledOrObservingRequestCannotBeDuplicated() {
        val state = SavedHistoryRetryState()

        assertEquals(500L, state.scheduleNext())
        assertNull(state.scheduleNext())
        assertTrue(state.beginScheduledAttempt())
        assertFalse(state.beginScheduledAttempt())
        state.markObserving()
        assertNull(state.scheduleNext())
    }

    @Test
    fun terminalStatesNeverRetry() {
        val complete = SavedHistoryRetryState().apply { markComplete() }
        val terminal = SavedHistoryRetryState().apply { markTerminal() }

        assertNull(complete.markRetryableFailure())
        assertNull(terminal.markRetryableFailure())
        assertNull(complete.scheduleNext())
        assertNull(terminal.scheduleNext())
    }
}
