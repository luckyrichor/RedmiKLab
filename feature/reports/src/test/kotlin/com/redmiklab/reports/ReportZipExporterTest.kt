package com.redmiklab.reports

import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportZipExporterTest {
    @Test
    fun exported_zip_contains_html_csv_and_json() {
        val bytes = ByteArrayOutputStream().also { ReportZipExporter.export(ReportSummary("run-a", "夜间诊断", "关联不等于因果"), it) }.toByteArray()
        val names = ZipInputStream(bytes.inputStream()).use { zip -> buildList { while (true) add(zip.nextEntry?.name ?: break) } }

        assertEquals(setOf("report.html", "snapshots.csv", "probes.csv", "traffic.csv", "report.json"), names.toSet())
    }
}
