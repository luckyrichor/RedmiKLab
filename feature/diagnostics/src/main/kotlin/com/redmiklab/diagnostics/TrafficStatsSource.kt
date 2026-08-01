package com.redmiklab.diagnostics

import com.redmiklab.model.TrafficWindow
import java.time.Instant

fun interface TrafficStatsSource {
    fun readWindow(start: Instant, end: Instant): TrafficWindow
}
