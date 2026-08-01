package com.redmiklab.app

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object ReportWorkspaceUi {
    enum class WorkspaceIcon { ANALYSIS, IMPORT }

    fun scroll(activity: Activity, page: LinearLayout): ScrollView =
        ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(AppUi.BACKGROUND)
            clipToPadding = false
            addView(
                page,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

    fun emptyState(
        activity: Activity,
        tag: String,
        icon: WorkspaceIcon,
        title: String,
        description: String,
        formatLabels: List<String>,
        actions: List<AppUi.ActionSpec>,
    ): LinearLayout = LinearLayout(activity).apply {
        this.tag = tag
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        minimumHeight = activity.dp(344)
        background = rounded(AppUi.SURFACE, activity.dp(16), AppUi.BORDER, activity.dp(1))
        setPadding(activity.dp(20), activity.dp(24), activity.dp(20), activity.dp(18))

        addView(
            WorkspaceIconView(activity, icon),
            LinearLayout.LayoutParams(activity.dp(58), activity.dp(58)).apply {
                bottomMargin = activity.dp(14)
            },
        )
        addView(TextView(activity).apply {
            text = title
            textSize = 17f
            setTextColor(AppUi.TEXT)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
        })
        addView(TextView(activity).apply {
            text = description
            textSize = 12f
            setTextColor(AppUi.MUTED)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.18f)
            setPadding(0, activity.dp(8), 0, activity.dp(12))
        })

        if (formatLabels.isNotEmpty()) {
            addView(formatBadges(activity, formatLabels), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                activity.dp(26),
            ).apply { bottomMargin = activity.dp(16) })
        }

        actions.forEach { action ->
            addView(
                AppUi.actionButton(activity, action.text, action.tone, action.onClick),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    activity.dp(40),
                ).apply { bottomMargin = activity.dp(8) },
            )
        }
    }

    fun stateCard(
        activity: Activity,
        tag: String,
        title: String,
        description: String,
        tone: StateTone = StateTone.NORMAL,
        actions: List<AppUi.ActionSpec> = emptyList(),
    ): LinearLayout = LinearLayout(activity).apply {
        this.tag = tag
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
        minimumHeight = activity.dp(220)
        background = rounded(AppUi.SURFACE, activity.dp(16), AppUi.BORDER, activity.dp(1))
        setPadding(activity.dp(20), activity.dp(22), activity.dp(20), activity.dp(18))
        addView(TextView(activity).apply {
            text = title
            textSize = 16f
            setTextColor(if (tone == StateTone.ERROR) AppUi.DANGER else AppUi.TEXT)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        addView(TextView(activity).apply {
            text = description
            textSize = 12f
            setTextColor(AppUi.MUTED)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.18f)
            setPadding(0, activity.dp(8), 0, if (actions.isEmpty()) 0 else activity.dp(16))
        })
        actions.forEach { action ->
            addView(
                AppUi.actionButton(activity, action.text, action.tone, action.onClick),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    activity.dp(40),
                ).apply { bottomMargin = activity.dp(8) },
            )
        }
    }

    enum class StateTone { NORMAL, ERROR }

    fun metricGrid(
        activity: Activity,
        metrics: List<Pair<String, String>>,
    ): LinearLayout = LinearLayout(activity).apply {
        tag = "analysis_metric_grid"
        orientation = LinearLayout.VERTICAL
        metrics.chunked(2).forEach { pair ->
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                pair.forEachIndexed { index, metric ->
                    addView(
                        metricCell(activity, metric.first, metric.second),
                        LinearLayout.LayoutParams(0, activity.dp(82), 1f).apply {
                            if (index == 0) marginEnd = activity.dp(5) else marginStart = activity.dp(5)
                        },
                    )
                }
                if (pair.size == 1) addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = activity.dp(10) })
        }
    }

    private fun metricCell(activity: Activity, label: String, value: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(AppUi.TONAL_SURFACE, activity.dp(12), Color.TRANSPARENT, 0)
            setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10))
            addView(TextView(activity).apply {
                text = label
                textSize = 11f
                setTextColor(AppUi.MUTED)
                includeFontPadding = false
            })
            addView(TextView(activity).apply {
                text = value
                textSize = 16f
                setTextColor(AppUi.TEXT)
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
                setPadding(0, activity.dp(5), 0, 0)
            })
        }

    private fun formatBadges(activity: Activity, labels: List<String>): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            labels.forEachIndexed { index, label ->
                addView(TextView(activity).apply {
                    text = label
                    textSize = 10f
                    setTextColor(AppUi.PRIMARY)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    background = rounded(AppUi.TONAL_SURFACE, activity.dp(7), Color.TRANSPARENT, 0)
                    setPadding(activity.dp(10), 0, activity.dp(10), 0)
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    activity.dp(24),
                ).apply { if (index > 0) marginStart = activity.dp(8) })
            }
        }

    private fun rounded(fill: Int, radius: Int, stroke: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(fill)
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    private fun Activity.dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

private class WorkspaceIconView(
    activity: Activity,
    private val icon: ReportWorkspaceUi.WorkspaceIcon,
) : View(activity) {
    private val density = resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppUi.TONAL_SURFACE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppUi.PRIMARY
        strokeWidth = density * 2f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppUi.PRIMARY
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = density * 2f
        canvas.drawRoundRect(
            RectF(inset, inset, width - inset, height - inset),
            density * 15f,
            density * 15f,
            backgroundPaint,
        )
        if (icon == ReportWorkspaceUi.WorkspaceIcon.ANALYSIS) drawAnalysis(canvas) else drawImport(canvas)
    }

    private fun drawAnalysis(canvas: Canvas) {
        val left = width * 0.27f
        val right = width * 0.73f
        val bottom = height * 0.70f
        canvas.drawLine(left, height * 0.30f, left, bottom, linePaint)
        canvas.drawLine(left, bottom, right, bottom, linePaint)
        val points = floatArrayOf(
            width * 0.31f, height * 0.60f,
            width * 0.42f, height * 0.49f,
            width * 0.52f, height * 0.56f,
            width * 0.66f, height * 0.36f,
        )
        for (i in 0 until points.size - 2 step 2) {
            canvas.drawLine(points[i], points[i + 1], points[i + 2], points[i + 3], linePaint)
        }
        for (i in points.indices step 2) canvas.drawCircle(points[i], points[i + 1], density * 2.2f, fillPaint)
    }

    private fun drawImport(canvas: Canvas) {
        val sheet = RectF(width * 0.31f, height * 0.24f, width * 0.69f, height * 0.74f)
        canvas.drawRoundRect(sheet, density * 3f, density * 3f, linePaint)
        val center = width * 0.50f
        canvas.drawLine(center, height * 0.35f, center, height * 0.58f, linePaint)
        canvas.drawLine(center, height * 0.58f, width * 0.42f, height * 0.50f, linePaint)
        canvas.drawLine(center, height * 0.58f, width * 0.58f, height * 0.50f, linePaint)
    }
}
