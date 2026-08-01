package com.redmiklab.app

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.redmiklab.reports.CompleteReport
import com.redmiklab.reports.ReportAnalyzer
import java.io.OutputStream

object PdfReportExporter {
    fun export(report: CompleteReport, output: OutputStream) {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(24, 32, 51) }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(90, 103, 125) }
        val lines = contentLines(report)
            .flatMap { wrapForPdf(it, 42) }
        var index = 0
        var pageNumber = 1
        while (index < lines.size) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            var y = 56f
            if (pageNumber == 1) {
                drawSignalChart(page.canvas, report, 42f, 250f, 511f, 120f)
            }
            while (index < lines.size && y < 800f) {
                val line = lines[index++]
                paint.textSize = if (pageNumber == 1 && y == 56f) 22f else 12f
                page.canvas.drawText(line, 42f, y, if (line.startsWith("本报告")) muted else paint)
                y += if (paint.textSize > 15f) 34f else 24f
                if (pageNumber == 1 && y in 230f..390f) y = 390f
            }
            document.finishPage(page)
            pageNumber++
        }
        document.writeTo(output)
        document.close()
    }

    internal fun wrapForPdf(text: String, maxChars: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        return text.chunked(maxChars)
    }

    private fun drawSignalChart(
        canvas: android.graphics.Canvas,
        report: CompleteReport,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
    ) {
        val samples = ReportAnalyzer.analyze(report).signalSamples
        if (samples.size < 2) return
        val chartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(49, 87, 213)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(220, 226, 238)
            strokeWidth = 1f
        }
        canvas.drawLine(left, top + height, left + width, top + height, axis)
        val min = samples.minOf { it.second }
        val max = samples.maxOf { it.second }
        val path = android.graphics.Path()
        samples.forEachIndexed { index, sample ->
            val x = left + width * index / (samples.size - 1)
            val ratio = (sample.second - min).toFloat() / (max - min).coerceAtLeast(1)
            val y = top + height - ratio * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, chartPaint)
    }

    fun contentLines(report: CompleteReport): List<String> {
        val analysis = ReportAnalyzer.analyze(report)
        return buildList {
            add("RedmiKLab 诊断报告")
            add(report.run.reportBaseName.ifBlank { report.run.runId })
            add("设备：${report.run.deviceModel.ifBlank { "未记录" }}")
            add("状态：${report.run.status}    模式：${if (report.run.runtimeMode == "STRICT") "严格" else "标准"}")
            add("计划窗口：${report.run.plannedStart} - ${report.run.plannedEnd}")
            add("实际窗口：${report.run.actualStart} - ${report.run.actualEnd}")
            add("快照：${report.summary.snapshotCount} 条")
            add("主动测速：${report.summary.probeCount} 轮 / ${report.summary.probeAttemptCount} 次尝试")
            add("平均下载：${report.summary.averageDownloadMbps?.let { "%.2f Mbps".format(it) } ?: "无数据"}")
            add("平均信号：${analysis.averageSignalDbm?.let { "%.1f dBm".format(it) } ?: "无数据"}")
            add("系统移动流量：${report.summary.systemMobileBytes} 字节")
            add("隧道观察流量：${report.summary.tunnelObservedBytes} 字节")
            add("")
            add("主要应用流量（Android 时段级近似统计）")
            analysis.topApps.take(12).forEach { add("${it.label}：${it.bytes} 字节") }
            add("")
            add("证据与提示")
            analysis.findings.forEach { add("• $it") }
            add("")
            add("本报告展示关联线索，不等同于因果结论。完整原始明细请使用 ZIP 或 JSON 格式。")
        }
    }
}
