package com.redmiklab.app

import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import com.redmiklab.reports.ReportFormat

object ReportFileNamePolicy {
    fun create(
        exportedAt: LocalDateTime,
        deviceModel: String,
        customDescription: String,
        start: LocalTime,
        end: LocalTime,
        startWasConfirmed: Boolean,
        endWasConfirmed: Boolean,
    ): String {
        val time = when {
            endWasConfirmed -> end
            startWasConfirmed -> start
            else -> exportedAt.toLocalTime()
        }
        val prefix = String.format(
            Locale.ROOT,
            "%04d%02d%02d-%02d%02d",
            exportedAt.year,
            exportedAt.monthValue,
            exportedAt.dayOfMonth,
            time.hour,
            time.minute,
        )
        val model = sanitize(deviceModel).ifBlank { "Redmi" }
        val description = sanitize(customDescription).ifBlank { "移动网络测试" }
        return normalizeForZip("$prefix-RedmiKLab-$model-$description")
    }

    fun normalizeForZip(value: String): String {
        val normalized = sanitize(value)
        if (normalized.isBlank()) return ""
        return if (normalized.endsWith(".zip", ignoreCase = true)) normalized else "$normalized.zip"
    }

    fun baseName(value: String): String = sanitize(value)
        .replace(Regex("\\.(zip|json|html|pdf)$", RegexOption.IGNORE_CASE), "")

    fun withFormat(value: String, format: ReportFormat): String {
        val base = baseName(value)
        return if (base.isBlank()) "" else "$base${format.suffix}"
    }

    private fun sanitize(value: String): String = value.trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), "_")
}
