package com.v2ray.ang.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil

/**
 * CPU and Wi-Fi locks held for the duration of profile test batches.
 *
 * Batch workers run for minutes (especially download speed tests) and must
 * keep making progress with the screen off: a foreground service alone does
 * not keep the CPU awake, and the Wi-Fi radio may sleep independently.
 * Create once per service (application context), acquire when a batch starts,
 * release when no batch is active anymore and in service teardown.
 */
internal class TestBatchLocks(context: Context) {
    private val appContext = context.applicationContext
    private val wakeLock: PowerManager.WakeLock? =
        appContext.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "v2rayNG:TestBatch")
            ?.apply { setReferenceCounted(false) }
    private val wifiLock: WifiManager.WifiLock? =
        appContext.getSystemService(WifiManager::class.java)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "v2rayNG:TestBatch")
            ?.apply { setReferenceCounted(false) }

    /** Idempotent: acquiring an already-held lock is a no-op. */
    fun acquire() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire()
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to acquire test wake lock", e)
        }
        try {
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to acquire test wifi lock", e)
        }
    }

    /** Idempotent: releasing a lock that is not held is a no-op. */
    fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to release test wake lock", e)
        }
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to release test wifi lock", e)
        }
    }
}
