package com.redmiklab.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.redmiklab.reports.CompleteReportFactory
import com.redmiklab.reports.ReportFormat
import com.redmiklab.reports.StandaloneReportExporter
import com.redmiklab.storage.DiagnosticDatabaseFactory
import com.redmiklab.storage.DiagnosticRunEntity

class ReportHistoryActivity : Activity() {
    private var pendingRun: DiagnosticRunEntity? = null
    private var pendingRunId: String? = null
    private var pendingFormat: ReportFormat = ReportFormat.ZIP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingFormat = ReportFormat.from(savedInstanceState?.getString(STATE_PENDING_FORMAT))
        pendingRunId = savedInstanceState?.getString(STATE_PENDING_RUN_ID)
        val page = AppUi.page(
            this,
            "历史诊断导出",
            "查看全部已完成或中断的诊断；可修改默认报告名、分析、导出或与其他报告比较。",
        )
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        setContentView(ScrollView(this).apply { addView(page) })
        loadRuns(list)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingRunId?.let { outState.putString(STATE_PENDING_RUN_ID, it) }
        outState.putString(STATE_PENDING_FORMAT, pendingFormat.name)
        super.onSaveInstanceState(outState)
    }

    private fun loadRuns(list: LinearLayout) {
        Thread {
            val runs = DiagnosticDatabaseFactory.get(this).diagnosticDao().terminalRuns()
            runOnUiThread {
                list.removeAllViews()
                if (runs.isEmpty()) {
                    list.addView(TextView(this).apply {
                        text = "还没有可用的历史诊断记录。"
                        setTextColor(AppUi.MUTED)
                        textSize = 15f
                    })
                } else runs.forEach { list.addView(runCard(it), AppUi.marginParams(this)) }
            }
        }.start()
    }

    private fun runCard(run: DiagnosticRunEntity): View {
        val card = AppUi.section(
            this,
            "${ReportText.time(run.startedAtEpochMs)} – ${ReportText.time(run.endedAtEpochMs)}",
            AppUi.SectionTone.REPORT,
        ) {}
        val subtitle = TextView(this).apply {
            text = buildString {
                append(ReportActivitySupport.historyDisplayName(run))
                append('\n')
                val duration = (run.endedAtEpochMs - run.startedAtEpochMs).coerceAtLeast(0) / 60_000
                append("${duration} 分钟 · ")
                append(if (run.runtimeMode == "STRICT") "严格模式" else "标准模式")
                append(" · 连接采集${if (run.connectionCaptureEnabled) "开启" else "关闭"}\n")
                append("${run.snapshotMinutes} 分钟快照 · ${if (run.status == "COMPLETED") "已完成" else "已中断"}")
            }
            textSize = 13f
            setTextColor(AppUi.MUTED)
        }
        card.addView(subtitle)
        val actions = LinearLayout(this).apply {
            tag = "history_actions"
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
            val firstRow = LinearLayout(this@ReportHistoryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(smallAction("分析报告") {
                    startActivity(
                        Intent(this@ReportHistoryActivity, ReportAnalysisActivity::class.java)
                            .putExtra(ReportAnalysisActivity.EXTRA_RUN_ID, run.runId),
                    )
                }, equal())
                addView(smallAction("导出报告") { showExport(run) }, equal())
            }
            val secondRow = LinearLayout(this@ReportHistoryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(smallAction("修改文件名") { showRename(run) }, equal())
                addView(smallAction("与其他报告比较") {
                    startActivity(
                        Intent(this@ReportHistoryActivity, ReportCompareActivity::class.java)
                            .putExtra(ReportCompareActivity.EXTRA_REPORT_A_RUN_ID, run.runId),
                    )
                }, equal())
            }
            addView(firstRow)
            addView(secondRow)
        }
        card.addView(actions)
        val details = LinearLayout(this).apply {
            tag = "history_details_content"
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(2), 0, 0)
        }
        card.addView(details)

        val detailsToggle = TextView(this).apply {
            tag = "history_details_toggle"
            text = "查看详情"
            textSize = 11f
            setTextColor(AppUi.PRIMARY)
            gravity = Gravity.CENTER
            includeFontPadding = false
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(4), 0, dp(4))
            setOnClickListener {
                val expanding = details.visibility != View.VISIBLE
                details.visibility = if (expanding) View.VISIBLE else View.GONE
                text = if (expanding) "收起详情" else "查看详情"
                if (expanding && details.childCount == 0) populateDetails(run, details)
            }
        }
        (card.getChildAt(0) as LinearLayout).addView(
            detailsToggle,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)),
        )
        return card
    }

    private fun populateDetails(run: DiagnosticRunEntity, details: LinearLayout) {
        details.addView(TextView(this).apply { text = "正在读取摘要…"; setTextColor(AppUi.MUTED) })
        Thread {
            val report = CompleteReportFactory(DiagnosticDatabaseFactory.get(this)).load(run.runId)
            runOnUiThread {
                details.removeAllViews()
                details.addView(TextView(this).apply {
                    text = "系统移动流量 ${ReportText.bytes(report.summary.systemMobileBytes)} · " +
                        "快照 ${report.summary.snapshotCount} 条 · 测速 ${report.summary.probeCount} 轮\n" +
                        "连接记录 ${report.summary.connectionFlowCount} 条 · 媒体/通知证据 ${report.summary.evidenceCount} 条"
                    textSize = 13f
                    setTextColor(AppUi.TEXT)
                    setPadding(0, dp(8), 0, dp(6))
                })
            }
        }.start()
    }

    private fun showRename(run: DiagnosticRunEntity) {
        DiagnosticSettingsDialogs.outputName(
            this,
            ReportActivitySupport.defaultBaseName(run),
            ReportActivitySupport.formatFromRun(run),
        ) { base, format ->
            Thread {
                DiagnosticDatabaseFactory.get(this).diagnosticDao()
                    .updateReportIdentity(run.runId, base, format.name)
                runOnUiThread {
                    Toast.makeText(this, "报告默认名称已保存", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }.start()
        }
    }

    private fun showExport(run: DiagnosticRunEntity) {
        DiagnosticSettingsDialogs.outputName(
            this,
            ReportActivitySupport.defaultBaseName(run),
            ReportActivitySupport.formatFromRun(run),
        ) { base, format ->
            Thread {
                val dao = DiagnosticDatabaseFactory.get(this).diagnosticDao()
                dao.updateReportIdentity(run.runId, base, format.name)
                val updatedRun = dao.runById(run.runId) ?: run
                runOnUiThread {
                    pendingRun = updatedRun
                    pendingRunId = updatedRun.runId
                    pendingFormat = format
                    startActivityForResult(
                        Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(format.mimeType)
                            .putExtra(Intent.EXTRA_TITLE, ReportFileNamePolicy.withFormat(base, format)),
                        EXPORT_REQUEST,
                    )
                }
            }.start()
        }
    }

    @Deprecated("Activity result API retained for minSdk-compatible single-document export")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != EXPORT_REQUEST || resultCode != RESULT_OK) return
        val runId = pendingRun?.runId ?: pendingRunId ?: return
        val uri = data?.data ?: return
        Thread {
            val result = runCatching {
                val run = DiagnosticDatabaseFactory.get(this).diagnosticDao().runById(runId)
                    ?: error("找不到待导出的历史诊断")
                contentResolver.openOutputStream(uri)?.use { output ->
                    if (pendingFormat == ReportFormat.PDF) {
                        PdfReportExporter.export(ReportActivitySupport.loadHistory(this, run.runId), output)
                    } else {
                        StandaloneReportExporter(DiagnosticDatabaseFactory.get(this))
                            .export(run.runId, pendingFormat, output)
                    }
                } ?: error("无法打开所选文件")
            }
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (result.isSuccess) "报告已导出" else "导出失败：${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }.start()
    }

    private fun smallAction(label: String, action: () -> Unit) =
        AppUi.actionButton(this, label, AppUi.ActionTone.NEUTRAL, action).apply {
        textSize = 12f
        setTypeface(typeface, Typeface.NORMAL)
    }

    private fun equal() = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
        marginEnd = dp(4)
        bottomMargin = dp(8)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val EXPORT_REQUEST = 2101
        const val STATE_PENDING_RUN_ID = "pending_run_id"
        const val STATE_PENDING_FORMAT = "pending_format"
    }
}
