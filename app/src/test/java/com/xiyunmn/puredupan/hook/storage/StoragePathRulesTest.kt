package com.xiyunmn.puredupan.hook.storage

import org.junit.Assert.assertThrows
import org.junit.Test

class StoragePathRulesTest {
    @Test
    fun rejectsTraversalAndIllegalNames() {
        assertThrows(IllegalArgumentException::class.java) {
            StoragePathRules.normalizeRelativePath("a/../b")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StoragePathRules.validateName("bad:name")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StoragePathRules.validateName("   ")
        }
    }
}
