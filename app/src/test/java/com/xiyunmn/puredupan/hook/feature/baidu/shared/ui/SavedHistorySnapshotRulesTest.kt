package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedHistorySnapshotRulesTest {
    private data class Item(val id: Int?, val transferTime: Long = 1, val name: String = "cached")
    private fun extend(native: List<Item>, cached: List<Item>, limit: Int = 5): List<Item>? =
        SavedHistorySnapshotRules.extend(native, cached, limit) { item ->
            item.id?.let { it to item.transferTime }
        }

    @Test
    fun nativePrefixKeepsFreshMetadataAndSnapshotOnlyAddsTail() {
        val fresh = listOf(Item(1, name = "renamed"), Item(2), Item(3))
        val cached = (1..5).map { Item(it) }
        val result = requireNotNull(extend(fresh, cached))

        assertEquals((1..5).toList(), result.map { it.id })
        assertSame(fresh[0], result[0])
        assertEquals("renamed", result[0].name)
    }

    @Test
    fun changedOrderOrTransferRejectsStaleTail() {
        val cached = (1..5).map { Item(it) }
        assertNull(extend(listOf(Item(2), Item(1), Item(3)), cached))
        assertNull(extend(listOf(Item(1, transferTime = 2), Item(2), Item(3)), cached))
        assertNull(extend(listOf(Item(9), Item(2), Item(3)), cached))
    }

    @Test
    fun refreshedShorterHistoryDropsPreviouslyCachedTail() {
        val native = (1..3).map { Item(it) }
        val oldSnapshot = (1..8).map { Item(it) }
        val newSnapshot = (1..4).map { Item(it) }

        assertEquals(8, extend(native, oldSnapshot, 10)?.size)
        assertEquals(4, extend(native, newSnapshot, 10)?.size)
        assertEquals(2, extend(native, newSnapshot, 2)?.size)
    }

    @Test
    fun absentNativeHistoryAndInvalidSnapshotsCannotResurrectRows() {
        assertNull(extend(emptyList(), listOf(Item(1))))
        assertNull(extend(listOf(Item(1)), emptyList()))
        assertNull(extend(listOf(Item(1)), listOf(Item(1), Item(null))))
        assertNull(extend(listOf(Item(1)), listOf(Item(1), Item(1))))
        assertNull(extend(listOf(Item(1)), (1..11).map { Item(it) }))
        assertNull(extend(listOf(Item(1), Item(2)), listOf(Item(1))))
    }

    @Test
    fun freshResponseCanReplaceOldCacheButCannotOverwriteNewerNativeBatch() {
        val old = (1..3).toList()
        val fresh = (8..12).toList()
        assertEquals(fresh, SavedHistorySnapshotRules.refreshed(old, fresh, 5, true) { it })
        assertNull(SavedHistorySnapshotRules.refreshed(old, fresh, 5, false) { it })
        assertEquals(fresh, SavedHistorySnapshotRules.refreshed(emptyList(), fresh, 5, true) { it })
        assertEquals(listOf(1, 2), SavedHistorySnapshotRules.refreshed(old, listOf(1, 2), 5, true) { it })
    }

    @Test
    fun expiredAndFutureSnapshotsAreRejected() {
        val written = 1_000_000L
        assertTrue(SavedHistorySnapshotRules.isFresh(written, written))
        assertTrue(SavedHistorySnapshotRules.isFresh(written, written + SavedHistorySnapshotRules.MAX_AGE_MILLIS))
        assertFalse(SavedHistorySnapshotRules.isFresh(written, written + SavedHistorySnapshotRules.MAX_AGE_MILLIS + 1))
        assertFalse(SavedHistorySnapshotRules.isFresh(written, written - 1))
        assertFalse(SavedHistorySnapshotRules.isFresh(0, written))
    }
}
