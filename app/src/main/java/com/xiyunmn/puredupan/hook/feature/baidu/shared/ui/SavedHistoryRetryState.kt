package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

/** Keeps saved-history retries bounded and prevents duplicate work during home-card redraws. */
internal class SavedHistoryRetryState(
    private val delaysMillis: LongArray = longArrayOf(500L, 1_500L, 4_000L),
) {
    internal enum class Phase {
        IDLE,
        SCHEDULED,
        OBSERVING,
        COMPLETE,
        TERMINAL,
    }

    var phase: Phase = Phase.IDLE
        private set

    var attemptCount: Int = 0
        private set

    fun scheduleNext(): Long? {
        if (phase != Phase.IDLE || attemptCount >= delaysMillis.size) return null
        phase = Phase.SCHEDULED
        return delaysMillis[attemptCount]
    }

    fun beginScheduledAttempt(): Boolean {
        if (phase != Phase.SCHEDULED) return false
        attemptCount += 1
        phase = Phase.IDLE
        return true
    }

    fun markObserving() {
        phase = Phase.OBSERVING
    }

    fun markRetryableFailure(): Long? {
        if (phase == Phase.COMPLETE || phase == Phase.TERMINAL) return null
        phase = Phase.IDLE
        return scheduleNext()
    }

    fun markComplete() {
        phase = Phase.COMPLETE
    }

    fun markTerminal() {
        phase = Phase.TERMINAL
    }

    fun isExhausted(): Boolean = attemptCount >= delaysMillis.size
}
