package com.redmiklab.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import com.redmiklab.reports.CompleteReport
import com.redmiklab.reports.CompleteReportFactory
import com.redmiklab.reports.ReportFormat
import com.redmiklab.reports.ReportZipImporter
import com.redmiklab.storage.DiagnosticDatabaseFactory
import com.redmiklab.storage.DiagnosticRunEntity

object ReportActivitySupport {
    fun loadLocal(activity: Activity, uri: Uri): CompleteReport {
        val name = queryName(activity, uri).lowercase()
        val mime = activity.contentResolver.getType(uri).orEmpty().lowercase()
        require(!name.endsWith(".html") && !name.endsWith(".pdf") &&
            mime !in setOf("text/html", "application/pdf")
        ) { "HTML 和 PDF 仅用于查看与分享；分析只支持 ZIP 和 JSON。" }
        val imported = activity.contentResolver.openInputStream(uri)?.use { input ->
            if (name.endsWith(".json") || mime == "application/json") {
                ReportZipImporter.importJson(input)
            } else ReportZipImporter.import(input)
        } ?: error("无法读取所选文件")
        return requireNotNull(imported.complete)
    }

    fun chooseHistory(activity: Activity, onChosen: (DiagnosticRunEntity) -> Unit) {
        Thread {
            val runs = DiagnosticDatabaseFactory.get(activity).diagnosticDao().terminalRuns()
            activity.runOnUiThread {
                if (runs.isEmpty()) {
                    android.widget.Toast.makeText(activity, "还没有历史诊断记录", android.widget.Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                lateinit var dialog: android.app.AlertDialog
                val list = ListView(activity).apply {
                    adapter = ReportHistoryChoiceAdapter(activity, runs)
                    divider = null
                    dividerHeight = 0
                    setPadding(0, 0, 0, 0)
                    clipToPadding = false
                    val density = resources.displayMetrics.density
                    val desiredHeight = (runs.size * 84 * density).toInt()
                    val minimumHeight = (120 * density).toInt()
                    val maximumHeight = (resources.displayMetrics.heightPixels * 0.58f).toInt()
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        desiredHeight.coerceIn(minimumHeight, maximumHeight),
                    )
                    setOnItemClickListener { _, _, position, _ ->
                        dialog.dismiss()
                        onChosen(runs[position])
                    }
                }
                dialog = AppUi.showSchemeADialog(
                    activity,
                    "选择历史诊断",
                    "选择一条已完成或中断的记录。",
                    list,
                    saveLabel = null,
                ) { false }
            }
        }.start()
    }

    fun openPicker(activity: Activity, requestCode: Int) {
        activity.startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/json")),
            requestCode,
        )
    }

    fun formatFromRun(run: DiagnosticRunEntity) = ReportFormat.from(run.preferredReportFormat)

    fun defaultBaseName(run: DiagnosticRunEntity): String =
        run.reportBaseName.ifBlank {
            "RedmiKLab-${run.deviceModel.ifBlank { "Redmi" }}-${ReportText.compactTime(run.plannedEndEpochMs)}"
        }

    fun historyDisplayName(run: DiagnosticRunEntity): String =
        run.reportBaseName.ifBlank { defaultBaseName(run) }

    fun historyChoiceLabel(run: DiagnosticRunEntity): String = buildString {
        append(historyDisplayName(run))
        append('\n')
        append(ReportText.time(run.startedAtEpochMs))
        append(" · ")
        append(if (run.runtimeMode == "STRICT") "严格模式" else "标准模式")
        append(" · ")
        append(if (run.status == "COMPLETED") "已完成" else "已中断")
    }

    fun loadHistory(activity: Activity, runId: String): CompleteReport =
        CompleteReportFactory(DiagnosticDatabaseFactory.get(activity)).load(runId)

    private fun queryName(activity: Activity, uri: Uri): String {
        activity.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
            }
        return uri.lastPathSegment.orEmpty()
    }
}

object ReportText {
    private val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val compact = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    fun time(epochMs: Long): String =
        java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).format(formatter)

    fun compactTime(epochMs: Long): String =
        java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).format(compact)

    fun bytes(value: Long): String = when {
        value >= 1_073_741_824 -> "%.2f GiB".format(value / 1_073_741_824.0)
        value >= 1_048_576 -> "%.2f MiB".format(value / 1_048_576.0)
        value >= 1_024 -> "%.2f KiB".format(value / 1_024.0)
        else -> "$value B"
    }
}
