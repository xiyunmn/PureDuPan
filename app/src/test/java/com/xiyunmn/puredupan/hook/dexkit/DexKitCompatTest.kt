package com.xiyunmn.puredupan.hook.dexkit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DexKitCompatTest {
    @Test
    fun targetResolutionPersistenceIsLimitedToScanningScope() {
        assertFalse(DexKitCompat.isTargetResolutionPersistenceAllowed())

        DexKitCompat.runWithScanningAllowed {
            assertTrue(DexKitCompat.isTargetResolutionPersistenceAllowed())

            DexKitCompat.runWithScanningAllowed {
                assertTrue(DexKitCompat.isTargetResolutionPersistenceAllowed())
            }

            assertTrue(DexKitCompat.isTargetResolutionPersistenceAllowed())
        }

        assertFalse(DexKitCompat.isTargetResolutionPersistenceAllowed())
    }

    @Test
    fun scanningScopeRestoresStateAfterException() {
        runCatching {
            DexKitCompat.runWithScanningAllowed {
                assertTrue(DexKitCompat.isTargetResolutionPersistenceAllowed())
                error("test failure")
            }
        }

        assertFalse(DexKitCompat.isTargetResolutionPersistenceAllowed())
    }
}
