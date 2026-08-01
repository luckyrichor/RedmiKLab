package com.redmiklab.diagnostics

object ThroughputCalculator {
    fun mbps(bytes: Long, elapsedMs: Long): Double? {
        if (bytes <= 0 || elapsedMs <= 0) return null
        return bytes * 8.0 / elapsedMs / 1_000.0
    }
}
