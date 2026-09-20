package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSaveCardLongPressBindingTest {
    private fun binding() = HomeSaveCardLongPressBinding.resolve(
        Card::class.java, CoordinateRow::class.java, NativeView::class.java,
    )!!

    @Test
    fun everySavedAndSubscriptionRowUsesItsOwnMenuDataAndRestoresCarouselPosition() {
        val card = Card()
        val binding = binding()
        for (subscription in listOf(false, true)) {
            for (index in 0..9) {
                val row = CoordinateRow(index.toString(), subscription)
                binding.listenerSetter.invoke(row, card)
                binding.withRowIndex(card, index, subscription) { row.longPress() }
                assertSame(row, card.menuRow)
                assertSame((if (subscription) card.subscriptions else card.saved)[index], card.menuItem)
                assertEquals(index, card.menuIndex)
                assertEquals(0, card.savedPosition())
                assertEquals(2, card.subscriptionPosition())
            }
        }
    }

    @Test
    fun nestedCallsAndNativeFailuresAlwaysRestoreBothIndices() {
        val card = Card()
        val binding = binding()
        val failure = IllegalStateException("native menu failed")
        try {
            binding.withRowIndex(card, 4, false) {
                binding.withRowIndex(card, 8, false) { assertEquals(8, card.savedPosition()) }
                assertEquals(4, card.savedPosition())
                binding.withRowIndex(card, 7, true) {
                    assertEquals(4, card.savedPosition())
                    assertEquals(7, card.subscriptionPosition())
                    throw failure
                }
            }
        } catch (error: IllegalStateException) {
            assertSame(failure, error)
        }
        assertEquals(0, card.savedPosition())
        assertEquals(2, card.subscriptionPosition())
    }

    @Test
    fun nativeCleanupCanTargetExtendedRowsWithoutNativeThreeRowDispatcher() {
        val card = Card()
        val binding = binding()
        for (subscription in listOf(false, true)) {
            val row = CoordinateRow("9", subscription)
            val content = ContentView()
            val cleanup = if (subscription) binding.subscriptionCleanup else binding.savedCleanup
            cleanup.invoke(card, false, row, content)
            assertTrue(content.highlighted)
            cleanup.invoke(card, true, row, content)
            assertEquals(false, content.highlighted)
            assertSame(row, card.cleanedRow)
            assertEquals(subscription, card.cleanedSubscription)
        }
    }

    @Test
    fun incompatibleCardsOrRowsAreRejectedInsteadOfGuessingTargets() {
        assertNull(HomeSaveCardLongPressBinding.resolve(Any::class.java, CoordinateRow::class.java, NativeView::class.java))
        assertNull(HomeSaveCardLongPressBinding.resolve(Card::class.java, NativeView::class.java, NativeView::class.java))
        assertNull(HomeSaveCardLongPressBinding.resolve(Card::class.java, CoordinateRow::class.java, String::class.java))
    }

    private open class NativeView
    private class ContentView : NativeView() { var highlighted = false }
    private class HorizontalScrollViewGroup : NativeView()
    private interface CoordinateListener { fun onLongClick(view: NativeView, x: Float, y: Float) }
    private class CoordinateRow(val tag: String, val subscription: Boolean) : NativeView() {
        private var listener: CoordinateListener? = null
        fun setOnLongClickWithCoordinate(listener: CoordinateListener) { this.listener = listener }
        fun longPress() { listener!!.onLongClick(this, 10f, 20f) }
    }

    @Suppress("UNUSED_PARAMETER")
    private class Card : CoordinateListener {
        private var currentIndex = 0
        private var linkCurrentIndex = 2
        val saved = List(10) { Any() }
        val subscriptions = List(10) { Any() }
        var menuRow: NativeView? = null
        var menuItem: Any? = null
        var menuIndex = -1
        var cleanedRow: NativeView? = null
        var cleanedSubscription = false
        fun savedPosition() = currentIndex
        fun subscriptionPosition() = linkCurrentIndex

        override fun onLongClick(view: NativeView, x: Float, y: Float) {
            val row = view as CoordinateRow
            val index = if (row.subscription) linkCurrentIndex else currentIndex
            if (row.tag != index.toString()) return
            menuRow = row
            menuIndex = index
            menuItem = (if (row.subscription) subscriptions else saved)[index]
        }

        fun removeLongClickEvent(remove: Boolean, group: HorizontalScrollViewGroup) = Unit
        fun removeSavePopupWindow(remove: Boolean, row: CoordinateRow, content: ContentView) {
            content.highlighted = !remove
            cleanedRow = row
            cleanedSubscription = false
        }
        fun removeClearPopupWindow(remove: Boolean, row: CoordinateRow, content: ContentView) {
            content.highlighted = !remove
            cleanedRow = row
            cleanedSubscription = true
        }
    }
}
