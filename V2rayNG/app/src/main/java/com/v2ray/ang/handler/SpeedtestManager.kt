package com.v2ray.ang.handler

import android.os.SystemClock
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object SpeedtestManager {

    data class RemoteEndpointInfo(
        val country: String?,
        val ipAddress: String?,
    )

    /**
     * Measures the time taken to establish a TCP connection to a given URL and port.
     *
     * @param url The URL to connect to.
     * @param port The port to connect to.
     * @return The connection time in milliseconds, or -1 if the connection failed.
     */
    fun socketConnectTime(url: String, port: Int, timeoutMs: Int = 1500): Long {
        var socket: Socket? = null
        val start = System.currentTimeMillis()

        try {
            socket = Socket()
            socket.connect(InetSocketAddress(url, port), timeoutMs)

            return System.currentTimeMillis() - start
        } catch (e: UnknownHostException) {
            LogUtil.e(AppConfig.TAG, "Unknown host: $url", e)
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "socketConnectTime IOException: ${e.message}")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to establish socket connection to $url:$port", e)
        } finally {
            socket?.let { s ->
                try {
                    if (!s.isClosed) {
                        s.close()
                    }
                } catch (closeEx: IOException) {
                }
            }
        }
        return -1
    }

    fun getRemoteIPInfo(): RemoteEndpointInfo? {
        val url = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
            .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        if (httpPort == 0) return null
        val content = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000,
                httpPort = httpPort,
                proxyUsername = proxyUsername,
                proxyPassword = proxyPassword
            )
        ) ?: return null
        val ipInfo = JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java) ?: return null

        val ip = listOf(
            ipInfo.ip,
            ipInfo.clientIp,
            ipInfo.ip_addr,
            ipInfo.query
        ).firstOrNull { !it.isNullOrBlank() }

        val country = listOf(
            ipInfo.country_code,
            ipInfo.country,
            ipInfo.countryCode,
            ipInfo.location?.country_code
        ).firstOrNull { !it.isNullOrBlank() }

        return RemoteEndpointInfo(
            country = country,
            ipAddress = ip,
        )
    }

    /**
     * Downloads [url] through a SOCKS proxy on loopback and measures the
     * average download speed in Mbps.
     *
     * Partial downloads count: the speed is computed from the bytes actually
     * received, so slow profiles report a low speed instead of failing.
     *
     * @param guid Stable profile identifier, used for logging only.
     * @param socksPort Local SOCKS proxy port serving the tested profile.
     * @param url Download URL, defaults to [AppConfig.SPEED_TEST_URL].
     * @param timeoutMs Overall deadline for the download.
     * @return Measured speed in Mbps, or 0 when nothing was received.
     */
    fun measureDownloadSpeedMbps(
        guid: String,
        socksPort: Int,
        url: String = AppConfig.SPEED_TEST_URL,
        timeoutMs: Int = AppConfig.SPEED_TEST_TIMEOUT_MS
    ): Float {
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(AppConfig.LOOPBACK, socksPort)))
            .connectTimeout(AppConfig.SPEED_TEST_CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Connection", "close")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LogUtil.w(AppConfig.TAG, "Speedtest download failed for $guid, code=${response.code}")
                    return 0f
                }
                val body = response.body ?: return 0f
                val start = SystemClock.elapsedRealtime()
                var receivedBytes = 0L
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (SystemClock.elapsedRealtime() - start > timeoutMs) break
                        val read = input.read(buffer)
                        if (read < 0) break
                        receivedBytes += read
                    }
                }
                val elapsedSeconds = (SystemClock.elapsedRealtime() - start) / 1000.0
                if (receivedBytes <= 0L || elapsedSeconds <= 0.0) {
                    return 0f
                }
                return (receivedBytes * 8f / elapsedSeconds / 1e6).toFloat()
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Speedtest download failed for $guid", e)
            return 0f
        }
    }
}
