package com.redmiklab.reports

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ReportZipExporter {
    fun export(summary: ReportSummary, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            write(zip, "report.html", HtmlReportRenderer.render(summary))
            write(zip, "snapshots.csv", "timestamp,is_mobile,radio,signal_dbm\n")
            write(zip, "probes.csv", "timestamp,dns_ms,https_ms,download_mbps,upload_mbps\n")
            write(zip, "traffic.csv", "window_start,window_end,package_name,mobile_bytes,approximate\n")
            write(zip, "report.json", "{\"schemaVersion\":1,\"runId\":\"${escape(summary.runId)}\",\"title\":\"${escape(summary.title)}\"}")
        }
    }

    private fun write(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
