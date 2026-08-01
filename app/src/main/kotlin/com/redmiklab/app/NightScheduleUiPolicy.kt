package com.redmiklab.app

import com.redmiklab.model.DiagnosticConfig
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NightScheduleUiPolicy {
    private val statusFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun nextStart(
        config: DiagnosticConfig,
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        var next = now.withHour(config.start.hour).withMinute(config.start.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(zoneId).toInstant().toEpochMilli()
    }

    fun status(
        enabled: Boolean,
        nextStartEpochMs: Long?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (!enabled) return "夜间计划：尚未安排"
        val next = nextStartEpochMs ?: return "夜间计划：已安排 · 下次开始时间待同步"
        val formatted = Instant.ofEpochMilli(next).atZone(zoneId).format(statusFormatter)
        return "夜间计划：已安排 · 下次开始 $formatted"
    }
}
