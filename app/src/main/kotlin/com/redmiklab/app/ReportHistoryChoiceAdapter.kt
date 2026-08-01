package com.redmiklab.app

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import com.redmiklab.storage.DiagnosticRunEntity

class ReportHistoryChoiceAdapter(
    private val activity: Activity,
    private val runs: List<DiagnosticRunEntity>,
) : BaseAdapter() {
    override fun getCount(): Int = runs.size

    override fun getItem(position: Int): DiagnosticRunEntity = runs[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        createRow(getItem(position))

    private fun createRow(run: DiagnosticRunEntity): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        minimumHeight = dp(84)
        setPadding(0, 0, 0, dp(8))
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(76)
            background = rounded(AppUi.SURFACE, dp(12), AppUi.BORDER, dp(1))
            setPadding(dp(12), dp(10), dp(10), dp(10))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(activity).apply {
                    text = ReportActivitySupport.historyDisplayName(run)
                    textSize = 13f
                    setTextColor(AppUi.TEXT)
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                })
                addView(TextView(activity).apply {
                    text = ReportText.time(run.startedAtEpochMs)
                    textSize = 11f
                    setTextColor(AppUi.MUTED)
                    includeFontPadding = false
                    setPadding(0, dp(6), 0, 0)
                })
                addView(TextView(activity).apply {
                    text = buildString {
                        append(if (run.runtimeMode == "STRICT") "严格模式" else "标准模式")
                        append(" · ")
                        append(if (run.status == "COMPLETED") "已完成" else "已中断")
                    }
                    textSize = 11f
                    setTextColor(AppUi.MUTED)
                    includeFontPadding = false
                    setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(activity).apply {
                tag = "history_choice_chevron"
                text = "›"
                textSize = 20f
                setTextColor(0xFF8A96A8.toInt())
                gravity = Gravity.CENTER
                includeFontPadding = false
            }, LinearLayout.LayoutParams(dp(26), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginStart = dp(8)
            })
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(fill)
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
