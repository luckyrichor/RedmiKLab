package com.redmiklab.app

import com.redmiklab.reports.CompleteReport
import com.redmiklab.reports.ReportRun
import com.redmiklab.reports.ReportNumericSummary
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class PdfReportExporterTest {
    @Test
    fun prepares_the_pdf_summary_from_real_report_values() {
        val report = CompleteReport(
            run = ReportRun(
                "run", "COMPLETED", "STANDARD", 1, 2, 1, 2,
                5, false, "测试报告", "PDF", "K60",
            ),
            summary = ReportNumericSummary(snapshotCount = 12, systemMobileBytes = 2_048),
        )

        val lines = PdfReportExporter.contentLines(report)

        assertTrue(lines.any { "快照：12 条" in it })
        assertTrue(lines.any { "系统移动流量：2048 字节" in it })
        assertTrue(lines.any { "不等同于因果" in it })
    }

    @Test
    fun wraps_long_pdf_text_without_silently_dropping_the_tail() {
        val text = "这是一个需要完整保留的很长结论".repeat(12)

        val wrapped = PdfReportExporter.wrapForPdf(text, 28)

        assertTrue(wrapped.size > 1)
        assertTrue(wrapped.joinToString("") == text)
    }
}
