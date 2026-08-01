package com.redmiklab.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.TextView

object AppUi {
    const val TEXT = 0xFF172131.toInt()
    const val MUTED = 0xFF697689.toInt()
    const val PRIMARY = 0xFF2864DC.toInt()
    const val SURFACE = 0xFFFFFFFF.toInt()
    const val BACKGROUND = 0xFFEDF2F7.toInt()
    const val BORDER = 0xFFE4E9F0.toInt()
    const val DANGER = 0xFFB42318.toInt()
    const val MODE = 0xFF7854C4.toInt()
    const val PERMISSION = 0xFFDB8722.toInt()
    const val RUN = 0xFF168A68.toInt()
    const val REPORT = 0xFF59677A.toInt()
    const val TONAL_SURFACE = 0xFFE9F0FC.toInt()
    const val INPUT_BORDER = 0xFFBAC6D5.toInt()
    const val ACTION_BORDER = 0xFFCFD8E5.toInt()
    const val STOP_BORDER = 0xFFE6C5C5.toInt()
    const val STOP_TEXT = 0xFFA33D3D.toInt()
    const val HELP_BORDER = 0xFF9AA7B8.toInt()
    private const val ROW_VALUE = 0xFF657287.toInt()
    private const val BADGE_SUCCESS_BACKGROUND = 0xFFEDF6F1.toInt()
    private const val BADGE_SUCCESS_TEXT = 0xFF167351.toInt()
    private const val BADGE_WARNING_BACKGROUND = 0xFFFFF2DF.toInt()
    private const val BADGE_WARNING_TEXT = 0xFFA35F00.toInt()

    enum class SectionTone { PARAMETER, MODE, PERMISSION, RUN, REPORT }

    enum class ActionTone { PRIMARY, TONAL, NEUTRAL, DANGER }

    enum class SettingStyle { VALUE, TOGGLE, BADGE }

    data class ActionSpec(
        val text: String,
        val tone: ActionTone = ActionTone.NEUTRAL,
        val onClick: () -> Unit,
    )

    class ChoiceField internal constructor(
        private val activity: Activity,
        private val choices: List<String>,
        selectedIndex: Int,
    ) {
        private val selectedText = TextView(activity).apply {
            textSize = 12f
            setTextColor(TEXT)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }

        var selectedIndex: Int = selectedIndex.coerceIn(choices.indices)
            private set

        val view: LinearLayout = LinearLayout(activity).apply {
            tag = "scheme_a_choice_field"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = rounded(SURFACE, activity.dp(10), INPUT_BORDER, activity.dp(1))
            setPadding(activity.dp(12), 0, activity.dp(8), 0)
            addView(selectedText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            addView(TextView(activity).apply {
                text = "▾"
                textSize = 13f
                setTextColor(MUTED)
                gravity = Gravity.CENTER
                includeFontPadding = false
            }, LinearLayout.LayoutParams(activity.dp(20), LinearLayout.LayoutParams.MATCH_PARENT))
            setOnClickListener { showChoices() }
        }

        init {
            require(choices.isNotEmpty()) { "Choice field requires at least one option" }
            renderSelection()
        }

        private fun renderSelection() {
            selectedText.text = choices[selectedIndex]
            view.contentDescription = choices[selectedIndex]
        }

        private fun showChoices() {
            val list = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(SURFACE, activity.dp(10), INPUT_BORDER, activity.dp(1))
                setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4))
            }
            lateinit var popup: PopupWindow
            choices.forEachIndexed { index, choice ->
                list.addView(TextView(activity).apply {
                    text = choice
                    textSize = 12f
                    gravity = Gravity.CENTER_VERTICAL
                    includeFontPadding = false
                    setTextColor(if (index == selectedIndex) PRIMARY else TEXT)
                    background = rounded(
                        if (index == selectedIndex) TONAL_SURFACE else Color.TRANSPARENT,
                        activity.dp(7),
                        Color.TRANSPARENT,
                        0,
                    )
                    setPadding(activity.dp(10), 0, activity.dp(10), 0)
                    setOnClickListener {
                        selectedIndex = index
                        renderSelection()
                        popup.dismiss()
                    }
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    activity.dp(38),
                ))
            }
            popup = PopupWindow(
                list,
                view.width.takeIf { it > 0 } ?: activity.dp(120),
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true,
            ).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                elevation = activity.dp(8).toFloat()
            }
            popup.showAsDropDown(view, 0, activity.dp(4))
        }
    }

    fun choiceField(
        activity: Activity,
        choices: List<String>,
        selectedIndex: Int,
    ): ChoiceField = ChoiceField(activity, choices, selectedIndex)

    class InlineHelpCard internal constructor(
        activity: Activity,
        title: String,
        message: String,
    ) {
        private val close = TextView(activity).apply {
            tag = "scheme_a_inline_help_close"
            text = "×"
            textSize = 16f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            includeFontPadding = false
            contentDescription = "关闭$title 说明"
            isClickable = true
            isFocusable = true
        }

        val view: LinearLayout = LinearLayout(activity).apply {
            tag = "scheme_a_inline_help_card"
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = rounded(TONAL_SURFACE, activity.dp(10), ACTION_BORDER, activity.dp(1))
            setPadding(activity.dp(12), activity.dp(8), activity.dp(8), activity.dp(10))
            addView(LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(activity).apply {
                    text = title
                    textSize = 12f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(TEXT)
                    includeFontPadding = false
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(close, LinearLayout.LayoutParams(activity.dp(28), activity.dp(28)))
            })
            addView(TextView(activity).apply {
                text = message
                textSize = 11f
                setTextColor(MUTED)
                setLineSpacing(0f, 1.12f)
            })
        }

        init {
            close.setOnClickListener { hide() }
        }

        fun show() {
            view.visibility = View.VISIBLE
        }

        fun hide() {
            view.visibility = View.GONE
        }

        fun toggle() {
            if (view.visibility == View.VISIBLE) hide() else show()
        }
    }

    fun inlineHelpCard(activity: Activity, title: String, message: String): InlineHelpCard =
        InlineHelpCard(activity, title, message)

    fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    fun section(
        activity: Activity,
        title: String,
        tone: SectionTone = SectionTone.PARAMETER,
        body: LinearLayout.() -> Unit,
    ): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            tag = tone
            background = rounded(SURFACE, activity.dp(14), BORDER, activity.dp(1))
            setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(6))
            addView(LinearLayout(activity).apply {
                tag = "scheme_a_section_header"
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = activity.dp(20)
                addView(View(activity).apply {
                    setBackgroundColor(sectionAccent(tone))
                }, LinearLayout.LayoutParams(activity.dp(3), activity.dp(20)))
                addView(TextView(activity).apply {
                    text = title
                    textSize = 13f
                    setTextColor(TEXT)
                    setTypeface(typeface, Typeface.BOLD)
                    includeFontPadding = false
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = activity.dp(6)
                })
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = activity.dp(7) })
            body()
        }

    fun LinearLayout.addSetting(
        activity: Activity,
        label: String,
        value: String,
        help: String,
        style: SettingStyle = SettingStyle.VALUE,
        onClick: () -> Unit,
    ): TextView {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = activity.dp(38)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val left = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(activity).apply {
                text = label
                textSize = 12f
                setTextColor(TEXT)
            })
            addView(helpIcon(activity, label, help))
        }
        val valueView = TextView(activity).apply {
            textSize = 11f
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        renderSettingValue(activity, valueView, value, style)
        row.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(
            valueView,
            when (style) {
                SettingStyle.VALUE -> LinearLayout.LayoutParams(activity.dp(102), LinearLayout.LayoutParams.WRAP_CONTENT)
                SettingStyle.TOGGLE -> LinearLayout.LayoutParams(activity.dp(30), activity.dp(17))
                SettingStyle.BADGE -> LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, activity.dp(22))
            },
        )
        if (style != SettingStyle.TOGGLE) {
            row.addView(TextView(activity).apply {
                text = "›"
                textSize = 16f
                setTextColor(0xFF929CAB.toInt())
                gravity = Gravity.CENTER
                includeFontPadding = false
            }, LinearLayout.LayoutParams(activity.dp(14), LinearLayout.LayoutParams.MATCH_PARENT))
        }
        addView(row)
        addView(divider(activity))
        return valueView
    }

    fun renderSettingValue(
        activity: Activity,
        view: TextView?,
        value: String,
        style: SettingStyle,
    ) {
        view ?: return
        when (style) {
            SettingStyle.VALUE -> {
                view.tag = style
                view.text = value
                view.contentDescription = value
                view.setTextColor(ROW_VALUE)
                view.background = ColorDrawable(Color.TRANSPARENT)
                view.setPadding(0, 0, activity.dp(4), 0)
                view.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            SettingStyle.TOGGLE -> {
                view.tag = "scheme_a_toggle"
                view.text = ""
                view.contentDescription = value
                view.background = ToggleDrawable(isToggleOn(value), activity.resources.displayMetrics.density)
                view.setPadding(0, 0, 0, 0)
            }
            SettingStyle.BADGE -> {
                val warning = value in setOf("去授权", "可选", "去管理", "未授权", "未设置")
                view.tag = "scheme_a_badge"
                view.text = value
                view.contentDescription = value
                view.setTextColor(if (warning) BADGE_WARNING_TEXT else BADGE_SUCCESS_TEXT)
                view.background = rounded(
                    if (warning) BADGE_WARNING_BACKGROUND else BADGE_SUCCESS_BACKGROUND,
                    activity.dp(6),
                    Color.TRANSPARENT,
                    0,
                )
                view.setPadding(activity.dp(6), 0, activity.dp(6), 0)
                view.gravity = Gravity.CENTER
            }
        }
    }

    fun LinearLayout.addAction(
        activity: Activity,
        text: String,
        primary: Boolean = false,
        danger: Boolean = false,
        tone: ActionTone? = null,
        onClick: () -> Unit,
    ): TextView {
        val resolvedTone = tone ?: when {
            danger -> ActionTone.DANGER
            primary -> ActionTone.PRIMARY
            else -> ActionTone.NEUTRAL
        }
        val button = actionButton(activity, text, resolvedTone, onClick)
        addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(36),
            ).apply { bottomMargin = activity.dp(8) },
        )
        return button
    }

    fun LinearLayout.addActionRow(
        activity: Activity,
        first: ActionSpec,
        second: ActionSpec,
    ): Pair<TextView, TextView> {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            tag = "scheme_a_two_column_action_row"
        }
        val firstButton = actionButton(activity, first.text, first.tone, first.onClick)
        val secondButton = actionButton(activity, second.text, second.tone, second.onClick)
        row.addView(
            firstButton,
            LinearLayout.LayoutParams(0, activity.dp(36), 1f).apply { marginEnd = activity.dp(4) },
        )
        row.addView(
            secondButton,
            LinearLayout.LayoutParams(0, activity.dp(36), 1f).apply { marginStart = activity.dp(4) },
        )
        addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(36),
            ).apply { bottomMargin = activity.dp(8) },
        )
        return firstButton to secondButton
    }

    fun actionButton(
        activity: Activity,
        text: String,
        tone: ActionTone = ActionTone.NEUTRAL,
        onClick: () -> Unit,
    ): TextView = TextView(activity).apply {
            this.text = text
            tag = tone
            textSize = 12f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            isClickable = true
            isFocusable = true
            elevation = 0f
            setTextColor(
                when (tone) {
                    ActionTone.PRIMARY -> Color.WHITE
                    ActionTone.TONAL -> PRIMARY
                    ActionTone.NEUTRAL -> TEXT
                    ActionTone.DANGER -> STOP_TEXT
                },
            )
            background = rounded(
                when (tone) {
                    ActionTone.PRIMARY -> PRIMARY
                    ActionTone.TONAL -> TONAL_SURFACE
                    ActionTone.NEUTRAL, ActionTone.DANGER -> SURFACE
                },
                activity.dp(9),
                when (tone) {
                    ActionTone.NEUTRAL -> ACTION_BORDER
                    ActionTone.DANGER -> STOP_BORDER
                    ActionTone.PRIMARY, ActionTone.TONAL -> Color.TRANSPARENT
                },
                activity.dp(1),
            )
            setOnClickListener { onClick() }
        }

    fun showSchemeADialog(
        activity: Activity,
        title: String,
        subtitle: String? = null,
        content: View,
        saveLabel: String? = "保存",
        onSave: () -> Boolean,
    ): AlertDialog {
        val panel = LinearLayout(activity).apply {
            tag = "scheme_a_dialog_panel"
            orientation = LinearLayout.VERTICAL
            background = rounded(SURFACE, activity.dp(17), 0xFFDCE3EC.toInt(), activity.dp(1))
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            addView(TextView(activity).apply {
                text = title
                textSize = 15f
                includeFontPadding = false
                setTextColor(TEXT)
                setTypeface(typeface, Typeface.BOLD)
            })
            subtitle?.takeIf { it.isNotBlank() }?.let { copy ->
                addView(TextView(activity).apply {
                    text = copy
                    textSize = 11f
                    includeFontPadding = false
                    setTextColor(MUTED)
                    setLineSpacing(0f, 1.15f)
                    setPadding(0, activity.dp(6), 0, activity.dp(10))
                })
            }
            addView(
                content,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    content.layoutParams?.height ?: LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, activity.dp(10), 0, 0)
        }
        val cancel = actionButton(activity, "取消", ActionTone.NEUTRAL) {}
            .apply { tag = "scheme_a_dialog_cancel" }
        val save = saveLabel?.let {
            actionButton(activity, it, ActionTone.PRIMARY) {}
                .apply { tag = "scheme_a_dialog_save" }
        }
        actions.addView(cancel, LinearLayout.LayoutParams(0, activity.dp(36), 1f).apply {
            if (save != null) marginEnd = activity.dp(4)
        })
        save?.let {
            actions.addView(it, LinearLayout.LayoutParams(0, activity.dp(36), 1f).apply {
                marginStart = activity.dp(4)
            })
        }
        panel.addView(actions)

        val dialog = AlertDialog.Builder(activity)
            .setView(panel)
            .create()
        cancel.setOnClickListener { dialog.dismiss() }
        save?.setOnClickListener {
            if (onSave()) dialog.dismiss()
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(
                activity.resources.displayMetrics.widthPixels - activity.dp(32),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            activity.resources.displayMetrics.widthPixels - activity.dp(32),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        return dialog
    }

    fun showHelp(anchor: View, title: String, message: String): PopupWindow {
        val activity = activityFrom(anchor.context)
            ?: error("说明浮层需要依附在 Activity 页面内")
        return showHelp(activity, anchor, title, message)
    }

    private fun showHelp(activity: Activity, anchor: View, title: String, message: String): PopupWindow {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(SURFACE, activity.dp(14), BORDER, activity.dp(1))
            setPadding(activity.dp(14), activity.dp(8), activity.dp(14), activity.dp(14))
            addView(LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(activity).apply {
                    text = title
                    textSize = 14f
                    setTextColor(TEXT)
                    setTypeface(typeface, Typeface.BOLD)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(activity).apply {
                    text = "×"
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(MUTED)
                    setPadding(activity.dp(8), 0, 0, 0)
                }, LinearLayout.LayoutParams(activity.dp(28), activity.dp(28)))
            })
            addView(TextView(activity).apply {
                text = message
                textSize = 12f
                setTextColor(MUTED)
                setLineSpacing(0f, 1.25f)
                setPadding(0, activity.dp(6), 0, 0)
            })
        }
        val popupWidth = minOf(activity.dp(300), activity.resources.displayMetrics.widthPixels - activity.dp(32))
        val popup = PopupWindow(
            content,
            popupWidth,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            false,
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = activity.dp(8).toFloat()
        }
        (content.getChildAt(0) as LinearLayout).getChildAt(1).setOnClickListener { popup.dismiss() }
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val maxLeft = (activity.resources.displayMetrics.widthPixels - popupWidth - activity.dp(12)).coerceAtLeast(activity.dp(12))
        val left = (location[0] - activity.dp(12)).coerceIn(activity.dp(12), maxLeft)
        content.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val belowTop = location[1] + anchor.height + activity.dp(6)
        val safeBottom = activity.resources.displayMetrics.heightPixels - activity.dp(12)
        val top = if (belowTop + content.measuredHeight <= safeBottom) {
            belowTop
        } else {
            (location[1] - content.measuredHeight - activity.dp(6)).coerceAtLeast(activity.dp(12))
        }
        popup.showAtLocation(helpPopupParent(anchor), Gravity.TOP or Gravity.START, left, top)
        return popup
    }

    internal fun helpPopupParent(anchor: View): View = anchor.rootView

    /** Applies the common Scheme A panel and button roles after an AlertDialog is shown. */
    fun styleDialog(
        dialog: AlertDialog,
        positiveTone: ActionTone = ActionTone.PRIMARY,
        negativeTone: ActionTone = ActionTone.NEUTRAL,
    ) {
        val activity = activityFrom(dialog.context) ?: return
        dialog.window?.setBackgroundDrawable(
            rounded(SURFACE, activity.dp(18), BORDER, activity.dp(1)),
        )
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), activity, positiveTone)
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), activity, negativeTone)
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), activity, ActionTone.NEUTRAL)
    }

    fun styleInput(input: EditText, activity: Activity) {
        input.setTextColor(TEXT)
        input.setHintTextColor(MUTED)
        input.textSize = 12f
        input.background = rounded(SURFACE, activity.dp(10), INPUT_BORDER, activity.dp(1))
        input.setPadding(activity.dp(12), 0, activity.dp(12), 0)
    }

    fun styleInput(input: Spinner, activity: Activity) {
        input.background = rounded(SURFACE, activity.dp(10), INPUT_BORDER, activity.dp(1))
        input.setPadding(activity.dp(10), 0, activity.dp(10), 0)
        (input.adapter as? SchemeAChoiceAdapter)?.select(input.selectedItemPosition)
        input.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                (input.adapter as? SchemeAChoiceAdapter)?.select(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    class SchemeAChoiceAdapter(
        context: android.content.Context,
        choices: List<String>,
        private val rowsForList: Boolean = false,
    ) : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, choices) {
        private var selectedPosition = if (rowsForList) AdapterView.INVALID_POSITION else 0

        fun select(position: Int) {
            selectedPosition = position
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
            row(position, convertView as? TextView, isDropDown = rowsForList)

        override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
            row(position, convertView as? TextView, isDropDown = true)

        private fun row(position: Int, recycled: TextView?, isDropDown: Boolean): TextView =
            (recycled ?: TextView(context)).apply {
                text = getItem(position).orEmpty()
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(if (isDropDown && position == selectedPosition) PRIMARY else TEXT)
                minHeight = dp(40)
                setPadding(dp(14), 0, dp(14), 0)
                background = rounded(
                    when {
                        !isDropDown -> Color.TRANSPARENT
                        position == selectedPosition -> TONAL_SURFACE
                        else -> SURFACE
                    },
                    dp(10),
                    if (isDropDown && position == selectedPosition) PRIMARY else BORDER,
                    dp(1),
                )
            }

        private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    }

    fun page(activity: Activity, title: String, subtitle: String? = null): LinearLayout =
        LinearLayout(activity).apply {
            val horizontalPadding = activity.dp(16)
            val baseTopPadding = activity.dp(14)
            val baseBottomPadding = activity.dp(24)
            tag = "scheme_a_report_page"
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            setPadding(horizontalPadding, baseTopPadding, horizontalPadding, baseBottomPadding)
            setOnApplyWindowInsetsListener { view, insets ->
                val topInset: Int
                val bottomInset: Int
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(
                        WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars(),
                    )
                    topInset = bars.top
                    bottomInset = bars.bottom
                } else {
                    @Suppress("DEPRECATION")
                    topInset = insets.systemWindowInsetTop
                    @Suppress("DEPRECATION")
                    bottomInset = insets.systemWindowInsetBottom
                }
                view.setPadding(
                    horizontalPadding,
                    baseTopPadding + topInset,
                    horizontalPadding,
                    baseBottomPadding + bottomInset,
                )
                insets
            }
            requestApplyInsets()
            addView(TextView(activity).apply {
                text = title
                textSize = 20f
                setTextColor(TEXT)
                setTypeface(typeface, Typeface.BOLD)
            })
            subtitle?.let {
                addView(TextView(activity).apply {
                    text = it
                    textSize = 13f
                    setTextColor(MUTED)
                    setPadding(0, activity.dp(4), 0, activity.dp(12))
                })
            }
        }

    fun marginParams(activity: Activity): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(12)
        }

    fun helpIcon(activity: Activity, title: String, help: String): TextView =
        TextView(activity).apply {
            text = "?"
            textSize = 10f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(0xFF667589.toInt())
            background = rounded(Color.TRANSPARENT, activity.dp(9), HELP_BORDER, activity.dp(1))
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                showHelp(this, title, help)
            }
            layoutParams = LinearLayout.LayoutParams(activity.dp(17), activity.dp(17)).apply {
                marginStart = activity.dp(6)
            }
        }

    private fun divider(activity: Activity) = View(activity).apply {
        setBackgroundColor(0xFFEDF0F4.toInt())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(1))
    }

    private fun sectionAccent(tone: SectionTone): Int = when (tone) {
        SectionTone.PARAMETER -> PRIMARY
        SectionTone.MODE -> MODE
        SectionTone.PERMISSION -> PERMISSION
        SectionTone.RUN -> RUN
        SectionTone.REPORT -> REPORT
    }

    private fun styleDialogButton(button: Button?, activity: Activity, tone: ActionTone) {
        button ?: return
        button.tag = tone
        button.isAllCaps = false
        button.textSize = 14f
        button.setTextColor(
            when (tone) {
                ActionTone.PRIMARY -> Color.WHITE
                ActionTone.TONAL -> PRIMARY
                ActionTone.NEUTRAL -> TEXT
                ActionTone.DANGER -> DANGER
            },
        )
        button.background = rounded(
            when (tone) {
                ActionTone.PRIMARY -> PRIMARY
                ActionTone.TONAL -> TONAL_SURFACE
                ActionTone.NEUTRAL, ActionTone.DANGER -> SURFACE
            },
            activity.dp(10),
            when (tone) {
                ActionTone.NEUTRAL -> BORDER
                ActionTone.DANGER -> DANGER
                ActionTone.PRIMARY, ActionTone.TONAL -> Color.TRANSPARENT
            },
            activity.dp(1),
        )
        button.setPadding(activity.dp(14), 0, activity.dp(14), 0)
    }

    private fun activityFrom(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int, strokeWidth: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(strokeWidth, stroke)
    }

    private fun isToggleOn(value: String): Boolean =
        value !in setOf("标准模式", "已关闭", "已停用", "关闭", "未开启")

    private class ToggleDrawable(
        private val enabled: Boolean,
        density: Float,
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val inset = 2f * density

        override fun draw(canvas: Canvas) {
            val radius = bounds.height() / 2f
            paint.color = if (enabled) PRIMARY else 0xFFB8C0CC.toInt()
            canvas.drawRoundRect(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                radius,
                radius,
                paint,
            )
            val thumbRadius = (bounds.height() - inset * 2) / 2f
            val centerX = if (enabled) bounds.right - inset - thumbRadius else bounds.left + inset + thumbRadius
            paint.color = Color.WHITE
            canvas.drawCircle(centerX, bounds.exactCenterY(), thumbRadius, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
