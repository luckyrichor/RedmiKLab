package com.redmiklab.app

import android.content.Context
import android.net.ConnectivityManager
import com.redmiklab.diagnostics.ThroughputCalculator
import com.redmiklab.model.ProbeFailure
import com.redmiklab.model.ProbeResult
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.time.TimeSource

/** A bounded HTTPS download probe. It reads at most [maximumBytes] even if a server ignores Range. */
class MobileHttpProbeRunner(
    context: Context,
    private val endpoint: String,
    private val onNetworkDiagnostic: (CellularNetworkDiagnostic) -> Unit = {},
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val recordedUnderlyingNetworkId = UnderlyingCellularSessionStore(context).read()

    fun run(maximumBytes: Long): ProbeResult {
        val timestamp = java.time.Instant.now()
        val host = runCatching { URI(endpoint).host ?: error("Endpoint has no host") }.getOrElse {
            return failed(timestamp, ProbeFailure.Dns)
        }
        val resolution = AndroidCellularNetworkResolver(
            connectivity,
            recordedUnderlyingNetworkId,
        ).resolve(NETWORK_WAIT_MS)
        onNetworkDiagnostic(resolution.diagnostic)
        val mobileNetwork = resolution.network ?: return failed(timestamp, ProbeFailure.Connection)
        val dnsStarted = TimeSource.Monotonic.markNow()
        val dnsLatency = runCatching {
            mobileNetwork.getAllByName(host)
            dnsStarted.elapsedNow().inWholeMilliseconds
        }.getOrElse { return failed(timestamp, ProbeFailure.Dns) }

        var connection: HttpsURLConnection? = null
        var httpsLatency: Long? = null
        return try {
            connection = (mobileNetwork.openConnection(URL(endpoint)) as HttpsURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Range", "bytes=0-${maximumBytes - 1}")
                setRequestProperty("Accept-Encoding", "identity")
            }
            val requestStarted = TimeSource.Monotonic.markNow()
            connection.connect()
            httpsLatency = requestStarted.elapsedNow().inWholeMilliseconds
            val downloadStarted = TimeSource.Monotonic.markNow()
            val download = connection.inputStream.use { input -> BoundedDownloadReader.read(input, maximumBytes) }
            val elapsed = downloadStarted.elapsedNow().inWholeMilliseconds
            val failure = download.failure
            if (failure == null) {
                ProbeResult(
                    timestamp,
                    dnsLatency,
                    httpsLatency,
                    ThroughputCalculator.mbps(download.bytesRead, elapsed),
                    null,
                    download.bytesRead,
                    null,
                )
            } else {
                failed(
                    timestamp,
                    if (failure is SocketTimeoutException) ProbeFailure.Timeout else ProbeFailure.Connection,
                    dnsLatency,
                    httpsLatency,
                    download.bytesRead,
                )
            }
        } catch (error: Throwable) {
            failed(
                timestamp,
                if (error is SocketTimeoutException) ProbeFailure.Timeout else ProbeFailure.Connection,
                dnsLatency,
                httpsLatency,
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun failed(
        timestamp: java.time.Instant,
        failure: ProbeFailure,
        dnsLatency: Long? = null,
        httpsLatency: Long? = null,
        consumedBytes: Long = 0,
    ) = ProbeResult(timestamp, dnsLatency, httpsLatency, null, null, consumedBytes, failure)

    private companion object {
        const val NETWORK_WAIT_MS = 10_000L
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
