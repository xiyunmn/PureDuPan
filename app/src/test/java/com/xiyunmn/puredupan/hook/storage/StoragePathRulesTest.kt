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
    fun preservesCloudFolderStructureAfterLegacyPrefix() {
        assertEquals(
            "Folder/file.txt",
            StoragePathRules.stripDefaultPublicPrefix("Download/BaiduNetdisk/Folder/file.txt"),
        )
        assertEquals(
            "Folder/Sub",
            StoragePathRules.stripDefaultPublicPrefix("Download/BaiduNetdisk/Folder/Sub"),
        )
    }

    @Test
    fun selectsHostOrTaskRelativeDownloadParent() {
        assertEquals(
            "",
            StoragePathRules.selectDownloadParent(
                "Download/BaiduNetdisk/Outer",
                "/",
                true,
            ),
        )
        assertEquals(
            "Target/Nested",
            StoragePathRules.selectDownloadParent(
                "Download/BaiduNetdisk/Outer/Target/Nested",
                "/Target/Nested",
                true,
            ),
        )
        assertEquals(
            "Outer/Target",
            StoragePathRules.selectDownloadParent(
                "Download/BaiduNetdisk/Outer/Target",
                null,
                false,
            ),
        )
        assertEquals(
            "Outer/Target/Nested",
            StoragePathRules.selectDownloadParent(
                "Download/BaiduNetdisk/Outer/Target/Nested",
                null,
                true,
            ),
        )
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
