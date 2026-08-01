package com.redmiklab.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class CompleteReportJsonTest {
    @Test
    fun round_trips_complete_report_tables_without_losing_nulls_or_unicode() {
        val report = CompleteReport(
            run = ReportRun(
                runId = "run-一",
                status = "COMPLETED",
                runtimeMode = "STRICT",
                plannedStart = 100,
                plannedEnd = 200,
                actualStart = 110,
                actualEnd = 205,
                snapshotMinutes = 5,
                connectionCaptureEnabled = true,
                reportBaseName = "夜间测试",
                preferredFormat = "JSON",
                deviceModel = "K80",
            ),
            summary = ReportNumericSummary(
                averageDownloadMbps = 8.25,
                snapshotCount = 2,
                systemMobileBytes = 900,
                tunnelObservedBytes = 700,
            ),
            tables = mapOf(
                "snapshots" to listOf(
                    mapOf("timestampEpochMs" to "110", "signalDbm" to "-92", "roaming" to null),
                ),
                "events" to listOf(mapOf("eventType" to "SNAPSHOT", "details" to "中文")),
            ),
        )

        val json = CompleteReportJson.encode(report)
        val decoded = CompleteReportJson.decode(json)

        assertTrue(json.contains("\"schemaVersion\":4"))
        assertEquals(report, decoded)
    }

    @Test
    fun streaming_decoder_round_trips_v4_without_materializing_the_source_text_again() {
        val report = CompleteReport(
            run = ReportRun(
                "stream", "INTERRUPTED", "STRICT", 1, 2, 1, 2,
                5, true, "流式报告", "JSON", "K80",
                terminalReason = "TEST", lastActionAt = 9,
            ),
            summary = ReportNumericSummary(snapshotCount = 1),
            tables = mapOf("events" to listOf(mapOf("details" to "完整内容", "failure" to null))),
        )

        val decoded = CompleteReportJson.decode(StringReader(CompleteReportJson.encode(report)))

        assertEquals(report, decoded)
    }

    @Test
    fun reads_legacy_summary_json_as_a_compatible_report() {
        val decoded = CompleteReportJson.decode(
            """{"schemaVersion":3,"runId":"legacy","status":"COMPLETED","runtimeMode":"STANDARD","plannedStart":1,"plannedEnd":2,"actualStart":1,"actualEnd":2,"averageDownloadMbps":3.5,"snapshotCount":4,"systemMobileBytes":50,"tunnelObservedBytes":40}""",
        )

        assertEquals("legacy", decoded.run.runId)
        assertEquals(3.5, decoded.summary.averageDownloadMbps)
        assertEquals(4, decoded.summary.snapshotCount)
        assertTrue(decoded.tables.isEmpty())
    }
}
