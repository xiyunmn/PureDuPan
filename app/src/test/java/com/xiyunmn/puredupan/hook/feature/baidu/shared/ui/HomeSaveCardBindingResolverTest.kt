package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext

class HomeSaveCardBindingResolverTest {
    @Test
    fun resolvesObfuscatedWriterAndPublishesTheSuppliedList() {
        val model = ObfuscatedModel()
        val writer = HomeSaveCardBindingResolver.listWriter(model.javaClass)!!
        val items = listOf("first", "second", "third", "fourth", "fifth")

        writer.invoke(model, true, items)

        assertSame(items, model.items)
        assertEquals(true, model.visible)
        assertSame(writer, HomeSaveCardBindingResolver.listWriter(model.javaClass))
    }

    @Test
    fun rejectsAmbiguousWritersInsteadOfChoosingByName() {
        assertNull(HomeSaveCardBindingResolver.listWriter(AmbiguousModel::class.java))
        assertNull(HomeSaveCardBindingResolver.listWriter(AmbiguousModel::class.java))
    }

    @Test
    fun rejectsStaticOrWrongReturnTypeMethods() {
        assertNull(HomeSaveCardBindingResolver.listWriter(NonWriterModel::class.java))
    }

    @Test
    fun resolvesRenamedStateCallbackAlongsideItsErasedBridge() {
        val card = SaveCard()
        val collector = ObfuscatedCollector(card)
        val state = CardState(Status.DATA, listOf("saved"), listOf("subscription"), true)
        val callback = HomeSaveCardBindingResolver.stateCollector(collector.javaClass, card.javaClass)!!

        assertEquals(1, collector.javaClass.declaredMethods.count { it.isBridge })
        assertSame(card, callback.owner.get(collector))
        callback.method.invoke(collector, state, continuation)
        assertSame(state, card.state)
    }

    @Test
    fun rejectsAmbiguousOrUnownedStateCallbacks() {
        assertNull(HomeSaveCardBindingResolver.stateCollector(AmbiguousCollector::class.java, SaveCard::class.java))
        assertNull(HomeSaveCardBindingResolver.stateCollector(ObfuscatedCollector::class.java, ObfuscatedModel::class.java))
    }

    @Test
    fun resolvesOnlySelectedCallbackOnTheCapturedCard() {
        val card = SaveCard()
        val listener = TabListener(card)
        val tab = NativeTab()
        val callback = HomeSaveCardBindingResolver.tabSelectionCallback(
            listener.javaClass, card.javaClass, tab.javaClass,
        )!!

        callback.method.invoke(listener, tab)

        assertSame(card, callback.owner.get(listener))
        assertSame(tab, card.selectedTab)
        assertNull(HomeSaveCardBindingResolver.tabSelectionCallback(
            listener.javaClass, ObfuscatedModel::class.java, tab.javaClass,
        ))
    }

    @Test
    fun bindsDomesticAndPrefixedInternationalSubscriptionRows() {
        val item = Subscription("fourth row")
        for (card in listOf(DomesticCard(), InternationalCard())) {
            val row = NativeView()
            val (render, bind) = HomeSaveCardBindingResolver.subscriptionBinder(
                card.javaClass, item.javaClass, row.javaClass,
            )!!
            render.invoke(card, bind.invoke(null, row), item)
            assertSame(item, row.item)
        }
    }

    @Test
    fun rejectsAmbiguousSubscriptionBindersAndUnrelatedFactories() {
        assertNull(HomeSaveCardBindingResolver.subscriptionBinder(
            AmbiguousSubscriptionCard::class.java, Subscription::class.java, NativeView::class.java,
        ))
        assertNull(HomeSaveCardBindingResolver.subscriptionBinder(
            InternationalCard::class.java, Subscription::class.java, String::class.java,
        ))
    }

    private val continuation = object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) = Unit
    }

    private enum class Status { DATA }
    private data class CardState(
        val status: Status, val saved: List<String>, val subscribed: List<String>, val visible: Boolean,
    )
    private class SaveCard {
        var state: CardState? = null
        var selectedTab: NativeTab? = null
    }
    private interface Collector<T> {
        fun a(value: T, continuation: Continuation<Unit>): Any
    }
    private class ObfuscatedCollector(private val card: SaveCard) : Collector<CardState> {
        override fun a(value: CardState, continuation: Continuation<Unit>): Any {
            card.state = value
            return Unit
        }
    }
    @Suppress("UNUSED_PARAMETER")
    private class AmbiguousCollector(private val card: SaveCard) {
        fun a(value: CardState, continuation: Continuation<Unit>): Any = Unit
        fun b(value: CardState, continuation: Continuation<Unit>): Any = Unit
    }
    private class NativeTab
    @Suppress("UNUSED_PARAMETER")
    private class TabListener(private val card: SaveCard) {
        fun onTabSelected(tab: NativeTab) { card.selectedTab = tab }
        fun onTabUnselected(tab: NativeTab) = Unit
        fun onTabReselected(tab: NativeTab) = Unit
    }
    private data class Subscription(val title: String)
    private class NativeView { var item: Subscription? = null }
    private class SubscribeToUpdatesLayoutBinding(val view: NativeView) {
        companion object {
            @JvmStatic fun bind(view: NativeView) = SubscribeToUpdatesLayoutBinding(view)
        }
    }
    private class Home25aiSubscribeToUpdatesLayoutBinding(val view: NativeView) {
        companion object {
            @JvmStatic fun a(view: NativeView) = Home25aiSubscribeToUpdatesLayoutBinding(view)
        }
    }
    private class DomesticCard {
        private fun setLinkUpdateData(binding: SubscribeToUpdatesLayoutBinding, item: Subscription) {
            binding.view.item = item
        }
    }
    private class InternationalCard {
        private fun a(binding: Home25aiSubscribeToUpdatesLayoutBinding, item: Subscription) {
            binding.view.item = item
        }
    }
    @Suppress("UNUSED_PARAMETER")
    private class AmbiguousSubscriptionCard {
        fun a(binding: SubscribeToUpdatesLayoutBinding, item: Subscription) = Unit
        fun b(binding: SubscribeToUpdatesLayoutBinding, item: Subscription) = Unit
    }

    @Suppress("UNUSED_PARAMETER")
    private class ObfuscatedModel {
        var items: List<String> = emptyList()
        var visible = false

        private fun a(show: Boolean, values: List<String>) {
            visible = show
            items = values
        }

        fun b(values: List<String>) = Unit
        fun c(show: Boolean) = Unit
    }

    @Suppress("UNUSED_PARAMETER")
    private class AmbiguousModel {
        fun a(show: Boolean, values: List<String>) = Unit
        fun b(show: Boolean, values: List<String>) = Unit
    }

    @Suppress("UNUSED_PARAMETER")
    private class NonWriterModel {
        fun a(show: Boolean, values: List<String>): Boolean = show

        companion object {
            @JvmStatic
            fun b(show: Boolean, values: List<String>) = Unit
        }
    }
}
