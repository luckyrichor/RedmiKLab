package com.redmiklab.reports

import java.util.Locale

/** Produces a self-contained SVG so reports remain viewable without internet access. */
object LineChartRenderer {
    fun render(values: List<Double?>): String {
        val present = values.filterNotNull()
        if (present.isEmpty()) return "<p>没有有效的吞吐数据可绘制。</p>"
        val width = 720.0
        val height = 220.0
        val padding = 28.0
        val maximum = present.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val points = values.mapIndexedNotNull { index, value ->
            value?.let {
                val x = if (values.size == 1) width / 2 else padding + index * (width - 2 * padding) / (values.size - 1)
                val y = height - padding - (it / maximum) * (height - 2 * padding)
                "%.1f,%.1f".format(Locale.US, x, y)
            }
        }.joinToString(" ")
        return """<section><h2>下载吞吐趋势</h2><svg viewBox="0 0 720 220" role="img" aria-label="下载吞吐趋势">
<line x1="$padding" y1="${height - padding}" x2="${width - padding}" y2="${height - padding}" stroke="#9ca3af"/>
<polyline fill="none" stroke="#2457A6" stroke-width="3" points="$points"/>
<text x="$padding" y="18" fill="#374151">最高 ${"%.2f".format(Locale.US, maximum)} Mbps</text></svg></section>"""
    }
}
