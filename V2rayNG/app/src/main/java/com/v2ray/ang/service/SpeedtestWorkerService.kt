package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.SpeedtestEvent
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Worker that measures per-profile download speed.
 *
 * Each batch owns its own CoroutineScope/dispatcher and can be cancelled
 * separately. For every profile a temporary core instance is started with a
 * single SOCKS inbound on a free loopback port (see
 * [CoreConfigManager.getV2rayConfig4SpeedProxy]), a 10 MB file is downloaded
 * through it, then the instance is stopped. This mirrors v2rayN's
 * SpeedtestService, which spins up one core per tested profile.
 *
 * Blocking download calls are not interruptible; cancelling the batch stops
 * scheduling new profiles while an in-flight download runs to its deadline.
 */
class SpeedtestWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onEvent: (SpeedtestEvent) -> Unit = {}
) : TestBatchWorker {
    private val job = SupervisorJob()
    private val dispatcher =
        Executors.newFixedThreadPool(AppConfig.SPEED_TEST_CONCURRENCY).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("SpeedtestBatchWorker"))
    private val usedPorts = Collections.synchronizedSet(mutableSetOf<Int>())

    private val runningCount = AtomicInteger(0)
    private val totalCount = AtomicInteger(0)

    fun start() {
        val jobs = guids.map { guid ->
            totalCount.incrementAndGet()
            scope.launch {
                runningCount.incrementAndGet()
                try {
                    val result = testSpeed(guid)
                    if (scope.isActive) {
                        onEvent(SpeedtestEvent.Result(guid, result))
                    }
                } catch (_: Throwable) {
                    // ignore
                } finally {
                    val count = totalCount.decrementAndGet()
                    val left = runningCount.decrementAndGet()
                    if (scope.isActive) {
                        onEvent(SpeedtestEvent.Progress("$left / $count"))
                    }
                }
            }
        }

        scope.launch {
            try {
                joinAll(*jobs.toTypedArray())
                if (isActive) {
                    onEvent(SpeedtestEvent.Finish("0"))
                }
            } catch (_: CancellationException) {
                // If cancelled, don't send finish event to avoid confusion
            } finally {
                close()
            }
        }
    }

    override fun cancel() {
        job.cancel()
    }

    private fun close() {
        try {
            dispatcher.close()
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun testSpeed(guid: String): Float {
        val config = MmkvManager.decodeServerConfig(guid) ?: return 0f
        if (config.configType == EConfigType.CUSTOM) {
            return 0f
        }
        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.alpn?.startsWith("h3") != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            val tcpTime = SpeedtestManager.socketConnectTime(
                config.server.orEmpty(),
                config.serverPort.orEmpty().toInt(),
                1000
            )
            if (tcpTime <= -1L) {
                return 0f
            }
        }

        val socksPort = allocatePort() ?: return 0f
        try {
            val configResult = CoreConfigManager.getV2rayConfig4SpeedProxy(context, guid, socksPort)
            if (!configResult.status) {
                LogUtil.w(AppConfig.TAG, "Speedtest config failed for $guid: ${configResult.errorMessage}")
                return 0f
            }
            val controller = try {
                CoreNativeManager.newCoreController(SpeedtestCoreCallback())
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Speedtest controller failed for $guid", e)
                return 0f
            }
            try {
                controller.startLoop(configResult.content, 0)
                if (!waitForPort(socksPort, AppConfig.SPEED_TEST_CONNECT_TIMEOUT_MS)) {
                    LogUtil.w(AppConfig.TAG, "Speedtest proxy not ready for $guid")
                    return 0f
                }
                return SpeedtestManager.measureDownloadSpeedMbps(guid, socksPort)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Speedtest failed for $guid", e)
                return 0f
            } finally {
                runCatching { controller.stopLoop() }
            }
        } finally {
            usedPorts.remove(socksPort)
        }
    }

    private fun allocatePort(): Int? {
        repeat(16) {
            try {
                val port = Utils.findRandomFreePort()
                if (usedPorts.add(port)) {
                    return port
                }
            } catch (_: Exception) {
                // ignore and retry
            }
        }
        return null
    }

    private fun waitForPort(port: Int, timeoutMs: Int): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(AppConfig.LOOPBACK, port), 250)
                    return true
                }
            } catch (_: Exception) {
                // not ready yet
            }
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return false
    }

    private class SpeedtestCoreCallback : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(l: Long, s: String?): Long = 0
    }
}
