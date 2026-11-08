package com.xiyunmn.puredupan.hook.storage

import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test

class StoragePathRulesTest {
    @Test
    fun removesCloudParentsWhileKeepingSelectedFolderHierarchy() {
        assertEquals("", StoragePathRules.selectDownloadParent("/软件/QQ音乐", null, true))
        assertEquals("", StoragePathRules.selectDownloadParent("/软件", "/", true))
        assertEquals("语音模块/驱动", StoragePathRules.selectDownloadParent("/开发/语音模块/驱动", "/语音模块/驱动", true))
        assertEquals("软件/QQ音乐", StoragePathRules.selectDownloadParent("/软件/QQ音乐", null, false))
    }

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
