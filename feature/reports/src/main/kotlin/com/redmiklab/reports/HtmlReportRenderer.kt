package com.redmiklab.reports

data class ReportMetric(val label: String, val value: String)
data class ReportSection(val title: String, val items: List<String>)

data class ReportSummary(
    val runId: String,
    val title: String,
    val conclusionNotice: String,
    val metrics: List<ReportMetric> = emptyList(),
    val findings: List<String> = emptyList(),
    val chartSvg: String = "",
    val sections: List<ReportSection> = emptyList(),
)

object HtmlReportRenderer {
    fun render(summary: ReportSummary): String = """<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>${escape(summary.title)}</title>
<style>body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;max-width:860px;margin:40px auto;padding:0 20px;color:#1d1d1f}section{border:1px solid #ddd;border-radius:12px;padding:16px;margin:16px 0}small{color:#666}table{width:100%;border-collapse:collapse}td,th{padding:9px;border-bottom:1px solid #ddd;text-align:left}li{margin:7px 0}</style>
</head><body><h1>${escape(summary.title)}</h1><section><p>${escape(summary.conclusionNotice)}</p><small>运行 ID：${escape(summary.runId)}</small></section>
${metrics(summary.metrics)}${summary.chartSvg}${sections(summary.sections)}${findings(summary.findings)}</body></html>"""

    private fun metrics(rows: List<ReportMetric>): String = if (rows.isEmpty()) "" else
        "<section><h2>摘要指标</h2><table><tbody>${rows.joinToString("") { "<tr><th>${escape(it.label)}</th><td>${escape(it.value)}</td></tr>" }}</tbody></table></section>"

    private fun findings(items: List<String>): String = if (items.isEmpty()) "" else
        "<section><h2>证据与提示</h2><ul>${items.joinToString("") { "<li>${escape(it)}</li>" }}</ul></section>"

    private fun sections(items: List<ReportSection>): String = items.joinToString("") { section ->
        "<section><h2>${escape(section.title)}</h2><ul>${section.items.joinToString("") { "<li>${escape(it)}</li>" }}</ul></section>"
    }

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
