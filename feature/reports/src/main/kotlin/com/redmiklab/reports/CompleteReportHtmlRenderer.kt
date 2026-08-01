package com.redmiklab.reports

object CompleteReportHtmlRenderer {
    fun render(report: CompleteReport): String {
        val analysis = ReportAnalyzer.analyze(report)
        val metrics = listOf(
            "运行状态" to report.run.status,
            "运行模式" to if (report.run.runtimeMode == "STRICT") "严格模式" else "标准模式",
            "设备型号" to report.run.deviceModel.ifBlank { "未记录" },
            "计划窗口" to "${time(report.run.plannedStart)} – ${time(report.run.plannedEnd)}",
            "实际窗口" to "${time(report.run.actualStart)} – ${time(report.run.actualEnd)}",
            "快照" to "${report.summary.snapshotCount} 条",
            "主动测速" to "${report.summary.probeCount} 轮 / ${report.summary.probeAttemptCount} 次尝试",
            "平均下载" to (report.summary.averageDownloadMbps?.let { "%.2f Mbps".format(it) } ?: "无数据"),
            "平均信号" to (analysis.averageSignalDbm?.let { "%.1f dBm".format(it) } ?: "无数据"),
            "系统移动流量" to "${report.summary.systemMobileBytes} 字节",
            "隧道观察流量" to "${report.summary.tunnelObservedBytes} 字节",
        )
        val topApps = analysis.topApps.take(10).joinToString("") {
            "<tr><td>${escape(it.label)}</td><td>${it.bytes}</td></tr>"
        }
        val previews = listOf("snapshots", "probes", "events", "appEvidence").joinToString("") { name ->
            tablePreview(name, report.tables[name].orEmpty())
        }
        return """<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escape(report.run.reportBaseName.ifBlank { "RedmiKLab 诊断报告" })}</title>
<style>body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;background:#f5f7fb;color:#172033;margin:0;padding:24px}.page{max-width:960px;margin:auto}section{background:#fff;border:1px solid #e3e8f2;border-radius:16px;padding:18px;margin:14px 0}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:9px;border-bottom:1px solid #edf0f5}h1{margin-bottom:4px}.notice{color:#657089}</style>
</head><body><main class="page"><h1>${escape(report.run.reportBaseName.ifBlank { "RedmiKLab 诊断报告" })}</h1>
<p class="notice">运行 ID：${escape(report.run.runId)}。报告中的关联线索不等同于因果结论。</p>
<section><h2>摘要指标</h2><table>${metrics.joinToString("") { "<tr><th>${it.first}</th><td>${escape(it.second)}</td></tr>" }}</table></section>
<section><h2>信号趋势</h2>${signalChart(analysis.signalSamples)}</section>
<section><h2>应用流量（时段级近似）</h2><table><tr><th>应用</th><th>字节</th></tr>$topApps</table></section>
<section><h2>证据与提示</h2><ul>${analysis.findings.joinToString("") { "<li>${escape(it)}</li>" }}</ul></section>
<section><h2>重要明细预览</h2>$previews</section>
</main></body></html>"""
    }

    private fun signalChart(samples: List<Pair<Long, Int>>): String {
        if (samples.isEmpty()) return "<p>无信号样本</p>"
        val min = samples.minOf { it.second }.coerceAtMost(-110)
        val max = samples.maxOf { it.second }.coerceAtLeast(-70)
        val points = samples.mapIndexed { index, sample ->
            val x = if (samples.size == 1) 20.0 else 20.0 + 720.0 * index / (samples.size - 1)
            val y = 180.0 - (sample.second - min).toDouble() / (max - min).coerceAtLeast(1) * 140.0
            "%.1f,%.1f".format(java.util.Locale.US, x, y)
        }.joinToString(" ")
        return """<svg viewBox="0 0 760 210" role="img" aria-label="信号强度折线图" style="width:100%;height:auto"><line x1="20" y1="180" x2="740" y2="180" stroke="#dfe5ef"/><polyline points="$points" fill="none" stroke="#3157d5" stroke-width="4"/></svg>"""
    }

    private fun tablePreview(name: String, rows: List<Map<String, String?>>): String {
        if (rows.isEmpty()) return "<details><summary>${escape(name)}（0 条）</summary><p>无数据</p></details>"
        val keys = rows.flatMap { it.keys }.distinct().take(8)
        val head = keys.joinToString("") { "<th>${escape(it)}</th>" }
        val body = rows.take(50).joinToString("") { row ->
            "<tr>${keys.joinToString("") { key -> "<td>${escape(row[key].orEmpty())}</td>" }}</tr>"
        }
        return "<details><summary>${escape(name)}（${rows.size} 条，预览前 50 条）</summary><div style=\"overflow:auto\"><table><tr>$head</tr>$body</table></div></details>"
    }

    private fun time(epochMs: Long): String = java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
