package com.redmiklab.diagnostics

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Calculates the end instant for the diagnostic window that is active at [startedAt]. */
object DiagnosticWindow {
    const val SAFE_FINALIZATION_LEAD_SECONDS = 120L
    const val MAX_FOREGROUND_SERVICE_RUNTIME_SECONDS = 6 * 60 * 60L

    fun endAfter(
        startedAt: Instant,
        endHour: Int,
        endMinute: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Instant {
        val localStart = startedAt.atZone(zoneId)
        var localEnd = localStart.toLocalDate().atTime(LocalTime.of(endHour, endMinute))
        if (!localEnd.isAfter(localStart.toLocalDateTime())) localEnd = localEnd.plusDays(1)
        return localEnd.atZone(zoneId).toInstant()
    }

    fun safeFinalizationAt(end: Instant): Instant = end.minusSeconds(SAFE_FINALIZATION_LEAD_SECONDS)

    fun boundedSafeFinalizationAt(startedAt: Instant, plannedEnd: Instant): Instant {
        val latestAllowedEnd = startedAt.plusSeconds(MAX_FOREGROUND_SERVICE_RUNTIME_SECONDS)
        val effectiveEnd = if (plannedEnd.isBefore(latestAllowedEnd)) plannedEnd else latestAllowedEnd
        return safeFinalizationAt(effectiveEnd)
    }
}
