package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarketingDialogBindingsTest {
    private class Activity
    private open class State
    private class SpecificState : State()

    private class StableOwner {
        @Suppress("UNUSED_PARAMETER")
        fun check3C1Guide(activity: Activity) = Unit
        @Suppress("UNUSED_PARAMETER")
        fun other(activity: Activity) = Unit
    }

    private class RenamedOwner {
        @Suppress("UNUSED_PARAMETER")
        fun q(activity: Activity) = Unit
        @Suppress("UNUSED_PARAMETER")
        fun other(activity: Activity) = Unit
        @Suppress("UNUSED_PARAMETER")
        fun wrongResult(activity: Activity): Boolean = false
    }

    private class OneState {
        val renamed = SpecificState()
        val unrelated = "config"
    }

    private class AmbiguousState {
        val first = State()
        val second = State()
    }

    private open class ParentDialog {
        fun close() = Unit
    }

    private class ChildDialog : ParentDialog() {
        @Suppress("UNUSED_PARAMETER")
        fun close(value: Boolean) = Unit
    }

    @Test
    fun stableOfferEntryWinsOverAnotherSameShapeMethod() {
        val owner = StableOwner::class.java
        val method = MarketingDialogBindings.offerEntry(
            owner, Activity::class.java, "check3C1Guide",
            owner.getDeclaredMethod("other", Activity::class.java),
        )
        assertEquals("check3C1Guide", method?.name)
    }

    @Test
    fun obfuscatedOfferRequiresObservedEnclosingMethodInsteadOfGuessingBySignature() {
        val owner = RenamedOwner::class.java
        assertNull(MarketingDialogBindings.offerEntry(owner, Activity::class.java, "check3C1Guide", null))
        val method = owner.getDeclaredMethod("q", Activity::class.java)
        assertEquals(
            method,
            MarketingDialogBindings.offerEntry(owner, Activity::class.java, "check3C1Guide", method),
        )
    }

    @Test
    fun rejectsForeignOwnerAndNonVoidEnclosingMethods() {
        val owner = RenamedOwner::class.java
        assertNull(
            MarketingDialogBindings.offerEntry(
                owner, Activity::class.java, "check3C1Guide",
                StableOwner::class.java.getDeclaredMethod("other", Activity::class.java),
            ),
        )
        assertNull(
            MarketingDialogBindings.offerEntry(
                owner, Activity::class.java, "check3C1Guide",
                owner.getDeclaredMethod("wrongResult", Activity::class.java),
            ),
        )
    }

    @Test
    fun liveDataStateMustBeUniqueEvenWhenItsFieldNameChanges() {
        assertEquals(
            "renamed",
            MarketingDialogBindings.offerState(OneState::class.java, State::class.java)?.name,
        )
        assertNull(MarketingDialogBindings.offerState(AmbiguousState::class.java, State::class.java))
    }

    @Test
    fun closeResolverFindsInheritedNoArgContractWithoutSelectingAnOverload() {
        val close = MarketingDialogBindings.noArgVoid(ChildDialog::class.java, "close")
        assertEquals(ParentDialog::class.java, close?.declaringClass)
        assertEquals(0, close?.parameterTypes?.size)
        assertNull(MarketingDialogBindings.noArgVoid(ChildDialog::class.java, "missing"))
    }
}
