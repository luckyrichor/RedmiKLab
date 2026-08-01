package com.redmiklab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class DiagnosticWindowTest {
    @Test
    fun uses_the_next_morning_end_for_an_overnight_window() {
        val start = instant("2026-07-13T00:30:00")
        val end = DiagnosticWindow.endAfter(start, 7, 0, ZoneId.of("UTC"))

        assertEquals(instant("2026-07-13T07:00:00"), end)
    }

    @Test
    fun uses_tomorrow_when_end_time_has_already_passed() {
        val start = instant("2026-07-13T23:00:00")
        val end = DiagnosticWindow.endAfter(start, 7, 0, ZoneId.of("UTC"))

        assertEquals(instant("2026-07-14T07:00:00"), end)
    }

    @Test
    fun reserves_two_minutes_for_safe_finalization() {
        val safeFinalization = DiagnosticWindow.safeFinalizationAt(instant("2026-07-13T06:00:00"))

        assertEquals(instant("2026-07-13T05:58:00"), safeFinalization)
    }

    @Test
    fun caps_manual_runs_at_the_six_hour_service_safety_limit() {
        val safeFinalization = DiagnosticWindow.boundedSafeFinalizationAt(
            startedAt = instant("2026-07-13T18:55:00"),
            plannedEnd = instant("2026-07-14T06:00:00"),
        )

        assertEquals(instant("2026-07-14T00:53:00"), safeFinalization)
    }

    @Test
    fun keeps_the_configured_end_when_it_is_inside_the_safety_limit() {
        val safeFinalization = DiagnosticWindow.boundedSafeFinalizationAt(
            startedAt = instant("2026-07-13T00:00:00"),
            plannedEnd = instant("2026-07-13T05:00:00"),
        )

        assertEquals(instant("2026-07-13T04:58:00"), safeFinalization)
    }

    private fun instant(value: String): Instant =
        LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
}
