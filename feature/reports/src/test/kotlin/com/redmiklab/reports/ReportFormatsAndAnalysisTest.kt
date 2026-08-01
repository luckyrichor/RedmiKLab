package com.redmiklab.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportFormatsAndAnalysisTest {
    private val report = CompleteReport(
        run = ReportRun(
            "run", "COMPLETED", "STRICT", 0, 3_600_000, 0, 3_600_000,
            5, true, "夜间测试", "ZIP", "K80",
        ),
        summary = ReportNumericSummary(
            averageDownloadMbps = 6.5,
            snapshotCount = 2,
            probeCount = 1,
            systemMobileBytes = 1_000,
            tunnelObservedBytes = 700,
        ),
        tables = mapOf(
            "snapshots" to listOf(
                mapOf("timestampEpochMs" to "0", "signalDbm" to "-88", "mobileDataActive" to "true"),
                mapOf("timestampEpochMs" to "300000", "signalDbm" to "-96", "mobileDataActive" to "false"),
            ),
            "appTraffic" to listOf(
                mapOf("displayName" to "抖音", "mobileBytes" to "600"),
                mapOf("displayName" to "微信", "mobileBytes" to "200"),
            ),
        ),
    )

    @Test
    fun exposes_only_the_four_confirmed_export_formats() {
        assertEquals(listOf("ZIP", "JSON", "HTML", "PDF"), ReportFormat.entries.map { it.name })
        assertEquals("application/json", ReportFormat.JSON.mimeType)
        assertEquals(".pdf", ReportFormat.PDF.suffix)
        assertTrue(ReportFormat.ZIP.importable)
        assertTrue(ReportFormat.JSON.importable)
        assertFalse(ReportFormat.HTML.importable)
        assertFalse(ReportFormat.PDF.importable)
    }

    @Test
    fun analysis_uses_report_evidence_and_marks_missing_or_disconnected_samples() {
        val analysis = ReportAnalyzer.analyze(report)

        assertEquals(-92.0, analysis.averageSignalDbm)
        assertEquals("抖音", analysis.topApps.first().label)
        assertEquals(600, analysis.topApps.first().bytes)
        assertTrue(analysis.findings.any { "移动数据不可用" in it })
        assertTrue(analysis.findings.none { "导致" in it })
    }

    @Test
    fun comparison_contains_structured_deltas_without_claiming_causation() {
        val other = report.copy(
            run = report.run.copy(runId = "other", deviceModel = "K60"),
            summary = report.summary.copy(averageDownloadMbps = 4.0, snapshotCount = 3),
        )

        val result = ReportComparator.compare(report, other)

        assertEquals(2.5, result.downloadMbpsDelta)
        assertEquals(-1, result.snapshotCountDelta)
        assertTrue(result.notice.contains("不能单独证明"))
    }
}
