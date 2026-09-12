package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCardLayoutRulesTest {
    @Test fun naturalRowsRemainMeasurableBeforeFirstLayout() {
        // 13.33.2 两个容器及所有行都改为 WRAP_CONTENT；此前因没有正数高度而退出。
        assertEquals(-2, HomeCardLayoutRules.rowHeight(-2, -2))
        assertEquals(-2, HomeCardLayoutRules.rowHeight(-2, 220))
        assertEquals(-2, HomeCardLayoutRules.rowHeight(-1, -2))
    }

    @Test fun legacyRowsKeepTheirHostDimensions() {
        assertEquals(162, HomeCardLayoutRules.rowHeight(-1, 162))
        assertEquals(330, HomeCardLayoutRules.rowHeight(-1, 330))
        assertEquals(180, HomeCardLayoutRules.rowHeight(180, 330))
    }
}
