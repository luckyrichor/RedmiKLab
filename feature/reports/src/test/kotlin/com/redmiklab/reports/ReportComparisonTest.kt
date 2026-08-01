package com.redmiklab.reports

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ReportComparisonTest {
    @Test
    fun compares_two_exported_run_summaries_without_claiming_causation() {
        val comparison = ReportComparison.compare(
            ImportedReport("k60", 10.0, 80),
            ImportedReport("k80", 8.0, 82),
        )

        assertTrue(comparison.contains("10.00 Mbps"))
        assertTrue(comparison.contains("8.00 Mbps"))
        assertTrue(comparison.contains("关联"))
    }

    @Test
    fun imports_summary_data_from_a_report_zip() {
        val archive = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("report.json"))
                zip.write("{\"schemaVersion\":1,\"runId\":\"k80-run\",\"averageDownloadMbps\":8.25,\"snapshotCount\":42}".toByteArray())
                zip.closeEntry()
            }
        }

        val report = ReportZipImporter.import(archive.toByteArray().inputStream())

        assertTrue(report.runId == "k80-run")
        assertTrue(report.averageDownloadMbps == 8.25)
        assertTrue(report.snapshotCount == 42)
        assertNotNull(report.complete)
    }

}
