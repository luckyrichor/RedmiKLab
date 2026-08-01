package com.redmiklab.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.redmiklab.reports.CompleteReport

class ReportImportActivity : Activity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = AppUi.page(
            this,
            "导入诊断报告",
            "从系统文件选择器的“最近”或“浏览”中选择报告。仅支持 ZIP 和 JSON；导入只读取，不会覆盖手机历史记录。",
        )
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(content)
        showEmptyWorkspace()
        setContentView(ReportWorkspaceUi.scroll(this, page))
    }

    private fun showEmptyWorkspace() {
        content.removeAllViews()
        content.addView(
            ReportWorkspaceUi.emptyState(
                activity = this,
                tag = "import_workspace_empty",
                icon = ReportWorkspaceUi.WorkspaceIcon.IMPORT,
                title = "选择手机中的诊断报告",
                description = "从“最近”或“浏览”选择文件。导入过程只读取报告，不会覆盖手机中的历史诊断。",
                formatLabels = listOf("ZIP", "JSON"),
                actions = listOf(localPickerAction("选择本地报告")),
            ),
            AppUi.marginParams(this),
        )
    }

    private fun localPickerAction(label: String) =
        AppUi.ActionSpec(label, AppUi.ActionTone.NEUTRAL) {
            ReportActivitySupport.openPicker(this, FILE_REQUEST)
        }

    @Deprecated("Activity result API retained for minSdk-compatible document selection")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_REQUEST || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        content.removeAllViews()
        content.addView(
            ReportWorkspaceUi.stateCard(
                activity = this,
                tag = "import_workspace_loading",
                title = "正在读取报告",
                description = "正在检查文件格式并整理报告摘要…",
            ),
            AppUi.marginParams(this),
        )
        Thread {
            val result = runCatching { ReportActivitySupport.loadLocal(this, uri) }
            runOnUiThread {
                result.fold(::render, ::showError)
            }
        }.start()
    }

    private fun render(report: CompleteReport) {
        content.removeAllViews()
        content.addView(AppUi.section(this, "已读取报告", AppUi.SectionTone.REPORT) {
            tag = "import_report_summary"
            line("名称", report.run.reportBaseName.ifBlank { report.run.runId })
            line("设备", report.run.deviceModel.ifBlank { "未记录" })
            line("状态", report.run.status)
            line("快照", "${report.summary.snapshotCount} 条")
            line("系统移动流量", ReportText.bytes(report.summary.systemMobileBytes))
            addView(TextView(this@ReportImportActivity).apply {
                text = "报告读取成功。你可以回到主页面进入“分析诊断报告”或“比较诊断报告”，再次从本地选择该文件。"
                textSize = 13f
                setTextColor(AppUi.MUTED)
                setPadding(0, dp(10), 0, 0)
            })
        }, AppUi.marginParams(this))
    }

    private fun showError(error: Throwable) {
        content.removeAllViews()
        content.addView(
            ReportWorkspaceUi.stateCard(
                activity = this,
                tag = "import_workspace_error",
                title = "无法读取这份报告",
                description = error.message ?: "请选择 RedmiKLab 导出的 ZIP 或 JSON 报告。",
                tone = ReportWorkspaceUi.StateTone.ERROR,
                actions = listOf(localPickerAction("重新选择本地报告")),
            ),
            AppUi.marginParams(this),
        )
        Toast.makeText(this, "导入失败：${error.message}", Toast.LENGTH_LONG).show()
    }

    private fun LinearLayout.line(label: String, value: String) {
        addView(TextView(this@ReportImportActivity).apply {
            text = "$label：$value"
            textSize = 14f
            setTextColor(AppUi.TEXT)
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val FILE_REQUEST = 2401
    }
}
