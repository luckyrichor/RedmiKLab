package com.redmiklab.reports

import com.redmiklab.storage.DiagnosticDatabase
import java.io.OutputStream
import java.io.OutputStreamWriter

class StandaloneReportExporter(private val database: DiagnosticDatabase) {
    fun export(runId: String, format: ReportFormat, output: OutputStream) {
        when (format) {
            ReportFormat.ZIP -> RoomReportExporter(database).export(runId, output)
            ReportFormat.JSON -> output.use {
                CompleteReportJson.write(
                    CompleteReportFactory(database).load(runId),
                    OutputStreamWriter(it, Charsets.UTF_8),
                )
            }
            ReportFormat.HTML -> output.use {
                it.write(CompleteReportHtmlRenderer.render(CompleteReportFactory(database).load(runId)).toByteArray())
            }
            ReportFormat.PDF -> error("PDF must be rendered with Android PdfDocument")
        }
    }
}
