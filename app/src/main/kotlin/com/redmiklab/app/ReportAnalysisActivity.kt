package com.redmiklab.app

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.redmiklab.reports.CompleteReport
import com.redmiklab.reports.ReportAnalysis
import com.redmiklab.reports.ReportAnalyzer

class ReportAnalysisActivity : Activity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = AppUi.page(
            this,
            "分析诊断报告",
            "数据可来自历史诊断或手机本地文件。仅支持 ZIP 和 JSON 格式；HTML/PDF 仅用于查看与分享。",
        )
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        page.addView(content)
        showEmptyWorkspace()
        setContentView(ReportWorkspaceUi.scroll(this, page))
        intent.getStringExtra(EXTRA_RUN_ID)?.let(::loadHistory)
    }

    private fun showEmptyWorkspace() {
        content.removeAllViews()
        content.addView(
            ReportWorkspaceUi.emptyState(
                activity = this,
                tag = "analysis_workspace_empty",
                icon = ReportWorkspaceUi.WorkspaceIcon.ANALYSIS,
                title = "选择一份诊断报告",
                description = "选择后将生成关键指标、信号趋势、应用流量构成和文字分析。",
                formatLabels = listOf("ZIP", "JSON"),
                actions = sourceActions(),
            ),
            AppUi.marginParams(this),
        )
    }

    private fun sourceActions(): List<AppUi.ActionSpec> = listOf(
        AppUi.ActionSpec("从历史记录选择", AppUi.ActionTone.NEUTRAL) {
            ReportActivitySupport.chooseHistory(this) { loadHistory(it.runId) }
        },
        AppUi.ActionSpec("从本地文件选择（最近 / 浏览）", AppUi.ActionTone.NEUTRAL) {
            ReportActivitySupport.openPicker(this, FILE_REQUEST)
        },
    )

    private fun resultSourceActions(): Pair<AppUi.ActionSpec, AppUi.ActionSpec> =
        AppUi.ActionSpec("从历史记录重新选择", AppUi.ActionTone.NEUTRAL) {
            ReportActivitySupport.chooseHistory(this) { loadHistory(it.runId) }
        } to AppUi.ActionSpec("从本地文件重新选择", AppUi.ActionTone.NEUTRAL) {
            ReportActivitySupport.openPicker(this, FILE_REQUEST)
        }

    private fun loadHistory(runId: String) {
        showLoading()
        Thread {
            val result = runCatching { ReportActivitySupport.loadHistory(this, runId) }
            runOnUiThread { result.fold(::render, ::showError) }
        }.start()
    }

    @Deprecated("Activity result API retained for minSdk-compatible document selection")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_REQUEST || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        showLoading()
        Thread {
            val result = runCatching { ReportActivitySupport.loadLocal(this, uri) }
            runOnUiThread { result.fold(::render, ::showError) }
        }.start()
    }

    private fun render(report: CompleteReport) {
        content.removeAllViews()
        val analysis = ReportAnalyzer.analyze(report)
        content.addView(AppUi.section(this, "更换分析报告", AppUi.SectionTone.REPORT) {
            val actions = resultSourceActions()
            AppUi.run {
                addActionRow(this@ReportAnalysisActivity, actions.first, actions.second)
            }
        }, AppUi.marginParams(this))
        content.addView(AppUi.section(this, "报告概览", AppUi.SectionTone.REPORT) {
            tag = "analysis_report_summary"
            addLine("报告", report.run.reportBaseName.ifBlank { report.run.runId })
            addLine("设备 / 模式", "${report.run.deviceModel.ifBlank { "未记录" }} / ${if (report.run.runtimeMode == "STRICT") "严格" else "标准"}")
        }, AppUi.marginParams(this))
        content.addView(AppUi.section(this, "关键指标", AppUi.SectionTone.REPORT) {
            addView(ReportWorkspaceUi.metricGrid(
                this@ReportAnalysisActivity,
                listOf(
                    "系统移动流量" to ReportText.bytes(report.summary.systemMobileBytes),
                    "隧道观察流量" to ReportText.bytes(report.summary.tunnelObservedBytes),
                    "平均下载" to (report.summary.averageDownloadMbps?.let { "%.2f Mbps".format(it) } ?: "无数据"),
                    "平均信号" to (analysis.averageSignalDbm?.let { "%.1f dBm".format(it) } ?: "无数据"),
                ),
            ))
        }, AppUi.marginParams(this))
        content.addView(AppUi.section(this, "信号趋势", AppUi.SectionTone.REPORT) {
            addView(SignalChartView(this@ReportAnalysisActivity, analysis))
        }, AppUi.marginParams(this))
        content.addView(AppUi.section(this, "应用流量构成", AppUi.SectionTone.REPORT) {
            addView(TrafficPieView(this@ReportAnalysisActivity, analysis))
            analysis.topApps.take(10).forEach { addLine(it.label, ReportText.bytes(it.bytes)) }
        }, AppUi.marginParams(this))
        content.addView(AppUi.section(this, "文字分析", AppUi.SectionTone.REPORT) {
            if (analysis.findings.isEmpty()) addLine("结果", "没有足够证据生成判断")
            analysis.findings.forEach { finding ->
                addView(TextView(this@ReportAnalysisActivity).apply {
                    text = "• $finding"
                    textSize = 14f
                    setTextColor(AppUi.TEXT)
                    setPadding(0, dp(4), 0, dp(4))
                })
            }
        }, AppUi.marginParams(this))
    }

    private fun LinearLayout.addLine(label: String, value: String) {
        addView(TextView(this@ReportAnalysisActivity).apply {
            text = "$label：$value"
            textSize = 14f
            setTextColor(AppUi.TEXT)
            setPadding(0, dp(4), 0, dp(4))
        })
    }

    private fun showLoading() {
        content.removeAllViews()
        content.addView(
            ReportWorkspaceUi.stateCard(
                activity = this,
                tag = "analysis_workspace_loading",
                title = "正在分析报告",
                description = "正在读取诊断数据并生成指标、图表和文字结论…",
            ),
            AppUi.marginParams(this),
        )
    }

    private fun showError(error: Throwable) {
        content.removeAllViews()
        content.addView(
            ReportWorkspaceUi.stateCard(
                activity = this,
                tag = "analysis_workspace_error",
                title = "无法分析这份报告",
                description = error.message ?: "报告内容无法读取，请重新选择 ZIP 或 JSON 报告。",
                tone = ReportWorkspaceUi.StateTone.ERROR,
                actions = sourceActions(),
            ),
            AppUi.marginParams(this),
        )
        Toast.makeText(this, "仅支持 ZIP 和 JSON 报告", Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_RUN_ID = "report_run_id"
        private const val FILE_REQUEST = 2201
    }
}

private class SignalChartView(
    activity: Activity,
    private val analysis: ReportAnalysis,
) : View(activity) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppUi.PRIMARY
        strokeWidth = activity.resources.displayMetrics.density * 2
        style = Paint.Style.STROKE
    }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5EAF2.toInt(); strokeWidth = 1f }
    init { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (180 * resources.displayMetrics.density).toInt()) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val values = analysis.signalSamples
        if (values.isEmpty()) {
            canvas.drawText("无信号样本", 16f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppUi.MUTED; textSize = 14f })
            return
        }
        val left = 24f
        val top = 18f
        val right = width - 18f
        val bottom = height - 24f
        repeat(4) { i -> canvas.drawLine(left, top + (bottom - top) * i / 3, right, top + (bottom - top) * i / 3, grid) }
        val min = values.minOf { it.second }.coerceAtMost(-110)
        val max = values.maxOf { it.second }.coerceAtLeast(-70)
        values.forEachIndexed { index, sample ->
            val x = if (values.size == 1) left else left + (right - left) * index / (values.size - 1)
            val y = bottom - (sample.second - min).toFloat() / (max - min).coerceAtLeast(1) * (bottom - top)
            if (index > 0) {
                val previous = values[index - 1]
                val px = if (values.size == 1) left else left + (right - left) * (index - 1) / (values.size - 1)
                val py = bottom - (previous.second - min).toFloat() / (max - min).coerceAtLeast(1) * (bottom - top)
                canvas.drawLine(px, py, x, y, line)
            }
        }
    }
}

private class TrafficPieView(
    activity: Activity,
    private val analysis: ReportAnalysis,
) : View(activity) {
    private val colors = intArrayOf(
        AppUi.PRIMARY,
        AppUi.MODE,
        AppUi.PERMISSION,
        AppUi.RUN,
        AppUi.REPORT,
    )
    init { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (190 * resources.displayMetrics.density).toInt()) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val slices = analysis.topApps.take(5)
        val total = slices.sumOf { it.bytes }
        if (total <= 0) {
            canvas.drawText("无应用流量数据", 16f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppUi.MUTED; textSize = 14f })
            return
        }
        val size = minOf(width, height) * 0.72f
        val rect = android.graphics.RectF(16f, 12f, 16f + size, 12f + size)
        var start = -90f
        slices.forEachIndexed { index, slice ->
            val sweep = slice.bytes.toFloat() / total * 360f
            canvas.drawArc(rect, start, sweep, true, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[index % colors.size] })
            start += sweep
        }
        canvas.drawCircle(rect.centerX(), rect.centerY(), size * 0.24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }
}
