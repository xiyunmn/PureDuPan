package com.xiyunmn.puredupan.hook.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StoragePathRulesTest {
    @Test
    fun normalizesSeparatorsAndStripsLegacyPrefix() {
        assertEquals("folder/sub", StoragePathRules.normalizeRelativePath("//folder\\sub//"))
        assertEquals("Movies/sub", StoragePathRules.stripDefaultPublicPrefix("Download/BaiduNetdisk/Movies/sub"))
        assertEquals("file.txt", StoragePathRules.stripDefaultPublicPrefix("BaiduNetdisk/file.txt"))
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
