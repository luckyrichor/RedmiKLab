package com.redmiklab.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.redmiklab.reports.CompleteReport
import com.redmiklab.reports.ReportComparator

class ReportCompareActivity : Activity() {
    private var reportA: CompleteReport? = null
    private var reportB: CompleteReport? = null
    private lateinit var statusA: TextView
    private lateinit var statusB: TextView
    private lateinit var result: LinearLayout
    private var historyRunA: String? = null
    private var historyRunB: String? = null
    private var localUriA: String? = null
    private var localUriB: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = AppUi.page(
            this,
            "比较诊断报告",
            "报告 A 与 B 均可从历史记录或本地 ZIP/JSON 选择。比较是只读操作。",
        )
        page.addView(sourceSection("报告 A", true), AppUi.marginParams(this))
        page.addView(sourceSection("报告 B", false), AppUi.marginParams(this))
        result = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(result)
        setContentView(ReportWorkspaceUi.scroll(this, page))
        if (savedInstanceState == null) {
            intent.getStringExtra(EXTRA_REPORT_A_RUN_ID)?.let { loadHistory(it, true) }
        } else {
            historyRunA = savedInstanceState.getString(STATE_HISTORY_A)
            historyRunB = savedInstanceState.getString(STATE_HISTORY_B)
            localUriA = savedInstanceState.getString(STATE_URI_A)
            localUriB = savedInstanceState.getString(STATE_URI_B)
            restoreSource(true)
            restoreSource(false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_HISTORY_A, historyRunA)
        outState.putString(STATE_HISTORY_B, historyRunB)
        outState.putString(STATE_URI_A, localUriA)
        outState.putString(STATE_URI_B, localUriB)
        super.onSaveInstanceState(outState)
    }

    private fun sourceSection(title: String, isA: Boolean): LinearLayout = AppUi.section(
        this,
        title,
        AppUi.SectionTone.REPORT,
    ) {
        tag = if (isA) "compare_source_a" else "compare_source_b"
        val status = TextView(this@ReportCompareActivity).apply {
            text = "尚未选择"
            setTextColor(AppUi.MUTED)
            setPadding(0, 0, 0, dp(8))
        }
        if (isA) statusA = status else statusB = status
        addView(status)
        AppUi.run {
            addAction(this@ReportCompareActivity, "从历史记录选择", tone = AppUi.ActionTone.NEUTRAL) {
                ReportActivitySupport.chooseHistory(this@ReportCompareActivity) { loadHistory(it.runId, isA) }
            }
            addAction(this@ReportCompareActivity, "从本地选择（最近 / 浏览）", tone = AppUi.ActionTone.NEUTRAL) {
                ReportActivitySupport.openPicker(
                    this@ReportCompareActivity,
                    if (isA) FILE_A_REQUEST else FILE_B_REQUEST,
                )
            }
        }
    }

    private fun loadHistory(runId: String, isA: Boolean) {
        if (isA) {
            historyRunA = runId
            localUriA = null
        } else {
            historyRunB = runId
            localUriB = null
        }
        Thread {
            val loaded = runCatching { ReportActivitySupport.loadHistory(this, runId) }
            runOnUiThread {
                loaded.fold({ assign(it, isA) }, { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() })
            }
        }.start()
    }

    @Deprecated("Activity result API retained for minSdk-compatible document selection")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || requestCode !in setOf(FILE_A_REQUEST, FILE_B_REQUEST)) return
        val uri = data?.data ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (requestCode == FILE_A_REQUEST) {
            localUriA = uri.toString()
            historyRunA = null
        } else {
            localUriB = uri.toString()
            historyRunB = null
        }
        loadLocal(uri, requestCode == FILE_A_REQUEST)
    }

    private fun loadLocal(uri: Uri, isA: Boolean) {
        Thread {
            val loaded = runCatching { ReportActivitySupport.loadLocal(this, uri) }
            runOnUiThread {
                loaded.fold(
                    { assign(it, isA) },
                    { Toast.makeText(this, "读取失败：${it.message}", Toast.LENGTH_LONG).show() },
                )
            }
        }.start()
    }

    private fun restoreSource(isA: Boolean) {
        val runId = if (isA) historyRunA else historyRunB
        val uri = if (isA) localUriA else localUriB
        when {
            runId != null -> loadHistory(runId, isA)
            uri != null -> loadLocal(Uri.parse(uri), isA)
        }
    }

    private fun assign(report: CompleteReport, isA: Boolean) {
        if (isA) {
            reportA = report
            statusA.text = "${report.run.reportBaseName.ifBlank { report.run.runId }} · ${report.run.deviceModel.ifBlank { "未记录型号" }}"
        } else {
            reportB = report
            statusB.text = "${report.run.reportBaseName.ifBlank { report.run.runId }} · ${report.run.deviceModel.ifBlank { "未记录型号" }}"
        }
        renderIfReady()
    }

    private fun renderIfReady() {
        val a = reportA ?: return
        val b = reportB ?: return
        val comparison = ReportComparator.compare(a, b)
        result.removeAllViews()
        result.addView(AppUi.section(this, "比较结果（A − B）", AppUi.SectionTone.REPORT) {
            tag = "compare_result_card"
            line("平均下载差", comparison.downloadMbpsDelta?.let { "%+.2f Mbps".format(it) } ?: "无可比数据")
            line("快照数量差", "%+d 条".format(comparison.snapshotCountDelta))
            line("系统移动流量差", "%+d 字节".format(comparison.systemMobileBytesDelta))
            addView(TextView(this@ReportCompareActivity).apply {
                text = comparison.notice
                textSize = 13f
                setTextColor(AppUi.MUTED)
                setPadding(0, dp(10), 0, 0)
            })
        }, AppUi.marginParams(this))
    }

    private fun LinearLayout.line(label: String, value: String) {
        addView(TextView(this@ReportCompareActivity).apply {
            text = "$label：$value"
            textSize = 15f
            setTextColor(AppUi.TEXT)
            setPadding(0, dp(5), 0, dp(5))
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_REPORT_A_RUN_ID = "report_a_run_id"
        private const val FILE_A_REQUEST = 2301
        private const val FILE_B_REQUEST = 2302
        private const val STATE_HISTORY_A = "history_a"
        private const val STATE_HISTORY_B = "history_b"
        private const val STATE_URI_A = "uri_a"
        private const val STATE_URI_B = "uri_b"
    }
}
