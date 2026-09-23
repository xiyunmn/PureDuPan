package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostThemeSnapshotBindingTest {
    interface Policy
    interface State { fun getValue(): Any }
    interface MutableState : State { fun setValue(value: Any) }
    class Snapshot(private var value: Any) : MutableState {
        var reads = 0
        var changes = 0
        override fun getValue(): Any { reads++; return value }
        override fun setValue(value: Any) {
            if (this.value != value) { this.value = value; changes++ }
        }
    }
    class Factory {
        companion object {
            @JvmStatic fun structuralEqualityPolicy(): Policy = object : Policy {}
            @JvmStatic fun mutableStateOf(value: Any, policy: Policy): MutableState = Snapshot(value)
        }
    }
    class InvalidFactory {
        companion object {
            @JvmStatic fun structuralEqualityPolicy(): Policy = object : Policy {}
            @JvmStatic fun mutableStateOf(value: Any, policy: Policy): Any = value
        }
    }

    @Test fun readsAndBothThemeDirectionsUseTheHostStateWithoutReplacingIt() {
        val binding = HostThemeSnapshotBinding.resolve(Factory::class.java, Policy::class.java, State::class.java, MutableState::class.java)!!
        val state = binding.create(true) as Snapshot
        assertTrue(binding.read(state))
        binding.update(state, false)
        assertFalse(binding.read(state))
        binding.update(state, false)
        binding.update(state, true)
        assertTrue(binding.read(state))
        assertEquals(3, state.reads)
        assertEquals(2, state.changes)
    }

    @Test fun refusesAnIncompatibleHostFactory() {
        assertNull(HostThemeSnapshotBinding.resolve(InvalidFactory::class.java, Policy::class.java, State::class.java, MutableState::class.java))
        assertNull(HostThemeSnapshotBinding.resolve(Factory::class.java, Policy::class.java, State::class.java, String::class.java))
    }
}
