package com.v2ray.ang.util

/**
 * Coalesces high-frequency batch progress updates (test results, progress
 * notifications) so large profile groups do not trigger redundant UI or
 * notification work on every single result.
 */
internal object BatchRefreshThrottle {
    const val INTERVAL_MS = 1000L

    fun shouldRefresh(nowMs: Long, lastMs: Long, intervalMs: Long = INTERVAL_MS): Boolean {
        return nowMs - lastMs >= intervalMs
    }
}
