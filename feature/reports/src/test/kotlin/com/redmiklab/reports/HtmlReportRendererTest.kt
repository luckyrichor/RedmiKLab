package com.redmiklab.reports

import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlReportRendererTest {
    @Test
    fun renders_self_contained_html_with_association_notice() {
        val html = HtmlReportRenderer.render(ReportSummary("run-a", "夜间诊断", "关联不等于因果"))

        assertTrue(html.contains("关联不等于因果"))
        assertTrue(html.contains("<html"))
    }

    @Test
    fun renders_metrics_and_evidence_as_offline_html() {
        val html = HtmlReportRenderer.render(
            ReportSummary(
                "run-a", "夜间诊断", "关联不等于因果",
                metrics = listOf(ReportMetric("平均下载", "12.5 Mbps")),
                findings = listOf("测速在 02:00–02:30 下降"),
            ),
        )

        assertTrue(html.contains("平均下载"))
        assertTrue(html.contains("12.5 Mbps"))
        assertTrue(html.contains("02:00–02:30"))
    }

    @Test
    fun renders_layered_data_completeness_and_media_sections() {
        val html = HtmlReportRenderer.render(
            ReportSummary(
                "run-a", "夜间诊断", "关联不等于因果",
                sections = listOf(
                    ReportSection("数据完整性", listOf("采集盲区 1 个")),
                    ReportSection("媒体时间线", listOf("实时媒体事件 8 条")),
                ),
            ),
        )

        assertTrue(html.contains("数据完整性"))
        assertTrue(html.contains("采集盲区 1 个"))
        assertTrue(html.contains("媒体时间线"))
    }
}
