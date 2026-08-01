package com.redmiklab.app

import java.io.InputStream

data class BoundedDownloadResult(
    val bytesRead: Long,
    val failure: Throwable?,
)

object BoundedDownloadReader {
    fun read(
        input: InputStream,
        maximumBytes: Long,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
    ): BoundedDownloadResult {
        require(maximumBytes >= 0)
        require(bufferSize > 0)
        val buffer = ByteArray(bufferSize)
        var bytes = 0L
        return try {
            while (bytes < maximumBytes) {
                val requested = minOf(buffer.size.toLong(), maximumBytes - bytes).toInt()
                val read = input.read(buffer, 0, requested)
                if (read < 0) break
                bytes += read
            }
            BoundedDownloadResult(bytes, null)
        } catch (error: Throwable) {
            BoundedDownloadResult(bytes, error)
        }
    }

    private const val DEFAULT_BUFFER_SIZE = 16 * 1024
}
