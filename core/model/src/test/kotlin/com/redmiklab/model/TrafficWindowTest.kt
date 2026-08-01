package com.redmiklab.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficWindowTest {
    @Test
    fun returns_highest_usage_apps_first() {
        val window = TrafficWindow(
            start = Instant.parse("2026-07-12T02:00:00Z"),
            end = Instant.parse("2026-07-12T02:30:00Z"),
            totalMobileBytes = 1_000,
            appUsage = listOf(
                AppTraffic("video", "Video", 300),
                AppTraffic("sync", "Sync", 600),
                AppTraffic("chat", "Chat", 100),
            ),
            isApproximate = true,
        )

        assertEquals(listOf("sync", "video"), window.topApps(limit = 2).map { it.packageName })
    }
}
