package com.redmiklab.reports

import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

data class ImportedReport(
    val runId: String,
    val averageDownloadMbps: Double?,
    val snapshotCount: Int,
    val complete: CompleteReport? = null,
)

object ReportComparison {
    fun compare(local: ImportedReport, imported: ImportedReport): String = buildString {
        append("本机（${local.runId}）平均下载：${local.averageDownloadMbps.displayMbps()}；")
        append("导入报告（${imported.runId}）平均下载：${imported.averageDownloadMbps.displayMbps()}。")
        append("快照数量：本机 ${local.snapshotCount}，导入报告 ${imported.snapshotCount}。")
        append("这是两份独立报告的关联比较，不能单独证明运营商或应用造成了问题。")
    }

    private fun Double?.displayMbps() = this?.let { "%.2f Mbps".format(java.util.Locale.US, it) } ?: "无有效数据"
}

object ReportZipImporter {
    fun import(input: InputStream): ImportedReport {
        return ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "report.json") return@use fromReader(InputStreamReader(zip, Charsets.UTF_8))
            }
            error("报告中没有 report.json")
        }
    }

    fun importJson(input: InputStream): ImportedReport =
        fromReader(InputStreamReader(input, Charsets.UTF_8))

    private fun fromReader(reader: InputStreamReader): ImportedReport {
        val report = CompleteReportJson.decode(reader)
        return ImportedReport(
            report.run.runId,
            report.summary.averageDownloadMbps,
            report.summary.snapshotCount,
            report,
        )
    }
}
