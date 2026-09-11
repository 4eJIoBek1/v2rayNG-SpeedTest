package com.v2ray.ang.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRefreshThrottleTest {

    @Test
    fun `allows refresh after interval elapsed`() {
        assertTrue(BatchRefreshThrottle.shouldRefresh(nowMs = 1500L, lastMs = 0L))
    }

    @Test
    fun `suppresses refresh inside interval`() {
        assertFalse(BatchRefreshThrottle.shouldRefresh(nowMs = 999L, lastMs = 0L))
    }

    @Test
    fun `allows first refresh from initial state`() {
        assertTrue(BatchRefreshThrottle.shouldRefresh(nowMs = 1000L, lastMs = 0L))
    }

    @Test
    fun `respects custom interval`() {
        assertTrue(BatchRefreshThrottle.shouldRefresh(nowMs = 500L, lastMs = 0L, intervalMs = 500L))
        assertFalse(BatchRefreshThrottle.shouldRefresh(nowMs = 499L, lastMs = 0L, intervalMs = 500L))
    }
}
