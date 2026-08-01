package com.redmiklab.app

import android.app.Activity
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import com.redmiklab.model.DiagnosticConfig
import com.redmiklab.reports.ReportFormat
import java.time.LocalTime
import java.util.Locale

object DiagnosticSettingsDialogs {
    fun snapshot(activity: Activity, current: Int, onSave: (Int) -> Unit) {
        val choices = intArrayOf(1, 2, 5, 10, 15, 30, 60)
        val picker = NumberPicker(activity).apply {
            minValue = 0
            maxValue = choices.lastIndex
            displayedValues = choices.map { "$it 分钟" }.toTypedArray()
            value = choices.indexOf(current).takeIf { it >= 0 } ?: choices.indexOf(5)
            wrapSelectorWheel = false
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(132),
            )
        }
        AppUi.showSchemeADialog(
            activity,
            "设置快照间隔",
            "诊断期间按该间隔记录网络、信号、屏幕、休眠和系统流量状态。运行中的诊断继续使用启动时配置。",
            picker,
        ) {
            onSave(choices[picker.value])
            true
        }
    }

    fun endpoints(activity: Activity, config: DiagnosticConfig, onSave: (String, String, Int) -> Unit) {
        val layout = form(activity)
        layout.addView(note(activity, "测速地址用于按计划下载少量数据，评估移动网络的 DNS、HTTPS 连接和下载吞吐。主地址失败 5 次后才会切换备用地址。"))
        val primary = input(activity, "主测速地址", config.probeEndpoint, InputType.TYPE_TEXT_VARIATION_URI)
        val fallback = input(activity, "备用测速地址", config.fallbackProbeEndpoint, InputType.TYPE_TEXT_VARIATION_URI)
        layout.addView(primary)
        layout.addView(fallback)
        val current = RetryDelayValue.fromSeconds(config.probeRetryDelaySeconds)
        val retryHelpText = "一次测速连接失败后，等待这段时间再进行下一次尝试。可按秒、分或时输入，换算后最长 300 秒。"
        val retryHelp = AppUi.inlineHelpCard(activity, "失败重试间隔", retryHelpText)
        val retryRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(activity).apply {
                text = "失败重试间隔"
                setTextColor(AppUi.TEXT)
                textSize = 14f
            })
            addView(
                AppUi.helpIcon(
                    activity,
                    "失败重试间隔",
                    retryHelpText,
                ).apply {
                    tag = "endpoint_retry_help_icon"
                    setOnClickListener { retryHelp.toggle() }
                },
            )
        }
        val retryValue = input(activity, "数值", current.value.toString(), InputType.TYPE_CLASS_NUMBER)
        val unit = AppUi.choiceField(
            activity,
            RetryDelayUnit.entries.map { it.label },
            current.unit.ordinal,
        )
        retryRow.addView(retryValue, LinearLayout.LayoutParams(0, activity.dp(42), 1f))
        retryRow.addView(unit.view, LinearLayout.LayoutParams(activity.dp(82), activity.dp(42)))
        layout.addView(retryRow)
        layout.addView(
            retryHelp.view,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(6)
                bottomMargin = activity.dp(8)
            },
        )
        AppUi.showSchemeADialog(activity, "设置测速地址", content = layout) {
            val value = retryValue.text.toString().toIntOrNull()
            val seconds = value?.let { RetryDelayUnit.entries[unit.selectedIndex].toSeconds(it) }
            if (primary.text.toString().startsWith("https://") &&
                fallback.text.toString().startsWith("https://") &&
                seconds != null && seconds in 0..DiagnosticConfig.MAX_PROBE_RETRY_DELAY_SECONDS
            ) {
                onSave(primary.text.toString().trim(), fallback.text.toString().trim(), seconds)
                true
            } else {
                retryValue.error = "主、备用地址须为 HTTPS，重试间隔换算后须为 0–300 秒"
                false
            }
        }
    }

    fun time(
        activity: Activity,
        title: String,
        current: LocalTime,
        onOpened: () -> Unit,
        onSave: (LocalTime) -> Unit,
    ) {
        onOpened()
        val layout = form(activity)
        layout.addView(note(activity, "使用 24 小时制；诊断窗口最长为 6 个小时。可用滚轮选择，也可在下方分别输入小时和分钟。"))
        val wheels = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
        }
        val hour = NumberPicker(activity).apply {
            minValue = 0
            maxValue = 23
            value = current.hour
            setFormatter { String.format(Locale.ROOT, "%02d", it) }
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        val minute = NumberPicker(activity).apply {
            minValue = 0
            maxValue = 59
            value = current.minute
            setFormatter { String.format(Locale.ROOT, "%02d", it) }
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        wheels.addView(hour, LinearLayout.LayoutParams(0, activity.dp(148), 1f))
        wheels.addView(TextView(activity).apply { text = ":"; textSize = 24f })
        wheels.addView(minute, LinearLayout.LayoutParams(0, activity.dp(148), 1f))
        layout.addView(wheels)
        val manualHour = input(
            activity,
            "小时",
            String.format(Locale.ROOT, "%02d", current.hour),
            InputType.TYPE_CLASS_NUMBER,
        ).apply {
            tag = "scheme_a_time_hour"
            filters = arrayOf(InputFilter.LengthFilter(2))
        }
        val manualMinute = input(
            activity,
            "分钟",
            String.format(Locale.ROOT, "%02d", current.minute),
            InputType.TYPE_CLASS_NUMBER,
        ).apply {
            tag = "scheme_a_time_minute"
            filters = arrayOf(InputFilter.LengthFilter(2))
        }
        val manualRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(manualHour, LinearLayout.LayoutParams(0, activity.dp(42), 1f))
            addView(TextView(activity).apply {
                text = ":"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(AppUi.TEXT)
            }, LinearLayout.LayoutParams(activity.dp(28), activity.dp(42)))
            addView(manualMinute, LinearLayout.LayoutParams(0, activity.dp(42), 1f))
        }
        layout.addView(manualRow)
        var synchronizing = false
        hour.setOnValueChangedListener { _, _, new ->
            if (!synchronizing) {
                synchronizing = true
                manualHour.setText(String.format(Locale.ROOT, "%02d", new))
                synchronizing = false
            }
        }
        minute.setOnValueChangedListener { _, _, new ->
            if (!synchronizing) {
                synchronizing = true
                manualMinute.setText(String.format(Locale.ROOT, "%02d", new))
                synchronizing = false
            }
        }
        manualHour.afterTextChanged { value ->
            val parsed = value.toIntOrNull()
            if (!synchronizing && parsed != null && parsed in 0..23 && hour.value != parsed) {
                synchronizing = true
                hour.value = parsed
                synchronizing = false
            }
        }
        manualMinute.afterTextChanged { value ->
            val parsed = value.toIntOrNull()
            if (!synchronizing && parsed != null && parsed in 0..59 && minute.value != parsed) {
                synchronizing = true
                minute.value = parsed
                synchronizing = false
            }
        }
        AppUi.showSchemeADialog(activity, title, content = layout) {
            val hourValue = manualHour.text.toString().toIntOrNull()
            val minuteValue = manualMinute.text.toString().toIntOrNull()
            val hourValid = hourValue != null && hourValue in 0..23
            val minuteValid = minuteValue != null && minuteValue in 0..59
            if (!hourValid) manualHour.error = "小时须为 0–23"
            if (!minuteValid) manualMinute.error = "分钟须为 0–59"
            if (!hourValid || !minuteValid) return@showSchemeADialog false
            onSave(LocalTime.of(hourValue!!, minuteValue!!))
            true
        }
    }

    fun outputName(
        activity: Activity,
        currentName: String,
        currentFormat: ReportFormat,
        onSave: (String, ReportFormat) -> Unit,
    ) {
        val layout = form(activity)
        layout.addView(note(activity, "这是本次或下一次诊断的默认导出文件名。导出时仍可修改；系统会自动移除非法字符并附加所选后缀。"))
        val name = input(activity, "名称", ReportFileNamePolicy.baseName(currentName), InputType.TYPE_CLASS_TEXT)
        val format = AppUi.choiceField(
            activity,
            ReportFormat.entries.map { it.suffix },
            currentFormat.ordinal,
        )
        layout.addView(name)
        layout.addView(format.view, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            activity.dp(42),
        ))
        AppUi.showSchemeADialog(activity, "设置输出文件名", content = layout) {
            val selected = ReportFormat.entries[format.selectedIndex]
            val base = ReportFileNamePolicy.baseName(name.text.toString())
            if (base.isBlank()) {
                name.error = "名称不能为空"
                false
            } else {
                onSave(base, selected)
                true
            }
        }
    }

    private fun form(activity: Activity) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, activity.dp(4), 0, 0)
    }

    private fun note(activity: Activity, value: String) = TextView(activity).apply {
        text = value
        textSize = 11f
        setTextColor(AppUi.MUTED)
        setLineSpacing(0f, 1.2f)
        setPadding(0, 0, 0, activity.dp(10))
    }

    private fun input(activity: Activity, label: String, value: String, type: Int) =
        EditText(activity).apply {
            hint = label
            setText(value)
            inputType = type
            setSelectAllOnFocus(false)
            AppUi.styleInput(this, activity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(42),
            ).apply { bottomMargin = activity.dp(8) }
        }

    private fun EditText.afterTextChanged(onChanged: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(value: Editable?) = onChanged(value?.toString().orEmpty())
        })
    }

    private fun Activity.dp(value: Int) = AppUi.run { dp(value) }
}
