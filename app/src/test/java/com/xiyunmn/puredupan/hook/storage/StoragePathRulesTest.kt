package com.xiyunmn.puredupan.hook.storage

import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test

class StoragePathRulesTest {
    @Test
    fun rootFolderAllowsMissingParentButRequiresSafeName() {
        assertEquals("资料", StoragePathRules.join(null, "资料"))
        assertEquals("资料", StoragePathRules.join("", "资料"))
        assertEquals("资料", StoragePathRules.join("/", "资料"))
        assertThrows(IllegalArgumentException::class.java) { StoragePathRules.join(null, null) }
        assertThrows(IllegalArgumentException::class.java) { StoragePathRules.join(null, "../资料") }
        assertThrows(IllegalArgumentException::class.java) { StoragePathRules.join("../外部", "资料") }
    }

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
