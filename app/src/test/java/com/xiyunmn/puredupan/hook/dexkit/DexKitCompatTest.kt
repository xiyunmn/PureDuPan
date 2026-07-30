package com.xiyunmn.puredupan.hook.dexkit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun scanMissDiagnosticIsScopedAndConsumable() {
        assertNull(DexKitCompat.consumeTargetScanMissDetail("target"))

        DexKitCompat.runWithScanningAllowed {
            DexKitCompat.recordTargetScanMissDetail("target", "candidateCount=0")
            assertEquals(
                "candidateCount=0",
                DexKitCompat.consumeTargetScanMissDetail("target"),
            )
            assertNull(DexKitCompat.consumeTargetScanMissDetail("target"))
        }

        assertNull(DexKitCompat.consumeTargetScanMissDetail("target"))
    }

    @Test
    fun targetSuccessDetailAlwaysIdentifiesResolutionSource() {
        assertEquals(
            "dexkit:owner.method",
            DexKitCompat.normalizeTargetSuccessDetail("owner.method"),
        )
        assertEquals(
            "fallback:owner.method",
            DexKitCompat.normalizeTargetSuccessDetail("fallback:owner.method"),
        )
        assertEquals(
            "resolver success; source detail missing",
            DexKitCompat.normalizeTargetSuccessDetail(null),
        )
    }
}
