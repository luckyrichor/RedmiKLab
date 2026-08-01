package com.redmiklab.app

import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedDownloadReaderTest {
    @Test
    fun reports_bytes_received_before_the_stream_failed() {
        val input = object : InputStream() {
            private var calls = 0

            override fun read(): Int = error("buffered read expected")

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (calls++ > 0) throw IOException("connection reset")
                repeat(3) { buffer[offset + it] = 1 }
                return 3
            }
        }

        val result = BoundedDownloadReader.read(input, maximumBytes = 5, bufferSize = 4)

        assertEquals(3, result.bytesRead)
        assertTrue(result.failure is IOException)
    }

    @Test
    fun stops_at_the_configured_byte_limit() {
        val input = ByteArray(10) { 1 }.inputStream()

        val result = BoundedDownloadReader.read(input, maximumBytes = 5, bufferSize = 4)

        assertEquals(5, result.bytesRead)
        assertEquals(null, result.failure)
    }
}
