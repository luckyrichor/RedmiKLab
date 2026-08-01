package com.redmiklab.app

import android.net.Network
import java.net.InetSocketAddress
import java.net.URI
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class HeartbeatTransportResult(
    val dnsBytes: Long,
    val requestBytes: Long,
    val responseBytes: Long,
    val failureStage: String?,
    val error: Throwable?,
)

data class HeartbeatResult(
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val endpoint: String,
    val dnsBytes: Long,
    val requestBytes: Long,
    val responseBytes: Long,
    val failureStage: String?,
    val errorType: String?,
)

object HeartbeatDecisionPolicy {
    fun shouldInvalidateNetwork(results: List<HeartbeatResult>): Boolean =
        results.isNotEmpty() && results.all { it.failureStage != null }
}

fun interface HeartbeatTransport<T> {
    fun execute(network: T, endpoint: String, maximumResponseBytes: Int): HeartbeatTransportResult
}

class NetworkHeartbeatRunner<T>(
    private val clock: () -> Long = System::currentTimeMillis,
    private val transport: HeartbeatTransport<T>,
) {
    fun run(network: T, endpoint: String, maximumResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES): HeartbeatResult {
        val startedAt = clock()
        val transportResult = transport.execute(network, endpoint, maximumResponseBytes)
        return HeartbeatResult(
            startedAtEpochMs = startedAt,
            completedAtEpochMs = clock(),
            endpoint = endpoint,
            dnsBytes = transportResult.dnsBytes,
            requestBytes = transportResult.requestBytes,
            responseBytes = transportResult.responseBytes,
            failureStage = transportResult.failureStage,
            errorType = transportResult.error?.javaClass?.simpleName,
        )
    }

    companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES = 4_096
    }
}

/** Performs a tiny HTTPS request on the explicitly selected physical Android network. */
class AndroidHeartbeatTransport : HeartbeatTransport<Network> {
    override fun execute(
        network: Network,
        endpoint: String,
        maximumResponseBytes: Int,
    ): HeartbeatTransportResult {
        var stage = "ENDPOINT"
        var requestBytes = 0L
        var responseBytes = 0L
        var socket: SSLSocket? = null
        return try {
            val uri = URI(endpoint)
            require(uri.scheme.equals("https", ignoreCase = true)) { "Heartbeat endpoint must use HTTPS" }
            val host = requireNotNull(uri.host) { "Heartbeat endpoint has no host" }
            val port = if (uri.port > 0) uri.port else 443
            val path = buildString {
                append(uri.rawPath.takeUnless { it.isNullOrBlank() } ?: "/")
                uri.rawQuery?.let { append('?').append(it) }
            }

            stage = "DNS"
            val address = network.getAllByName(host).first()
            stage = "CONNECT"
            val raw = network.socketFactory.createSocket().apply {
                connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                soTimeout = READ_TIMEOUT_MS
            }
            stage = "TLS_HANDSHAKE"
            socket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, host, port, true) as SSLSocket
            socket.sslParameters = socket.sslParameters.apply {
                endpointIdentificationAlgorithm = "HTTPS"
            }
            socket.startHandshake()

            val request = (
                "GET $path HTTP/1.1\r\n" +
                    "Host: $host\r\n" +
                    "Range: bytes=0-0\r\n" +
                    "Accept-Encoding: identity\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray(Charsets.US_ASCII)
            stage = "REQUEST_WRITE"
            socket.outputStream.write(request)
            socket.outputStream.flush()
            requestBytes = request.size.toLong()

            stage = "RESPONSE_READ"
            val buffer = ByteArray(512)
            while (responseBytes < maximumResponseBytes) {
                val remaining = (maximumResponseBytes - responseBytes).toInt()
                val read = socket.inputStream.read(buffer, 0, minOf(buffer.size, remaining))
                if (read < 0) break
                responseBytes += read
            }
            HeartbeatTransportResult(0, requestBytes, responseBytes, null, null)
        } catch (error: Throwable) {
            HeartbeatTransportResult(0, requestBytes, responseBytes, stage, error)
        } finally {
            runCatching { socket?.close() }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 10_000
    }
}
