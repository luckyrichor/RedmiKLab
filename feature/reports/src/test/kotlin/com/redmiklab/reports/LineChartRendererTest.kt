package com.redmiklab.reports

import org.junit.Assert.assertTrue
import org.junit.Test

class LineChartRendererTest {
    @Test
    fun renders_a_self_contained_svg_for_valid_throughput_points() {
        val svg = LineChartRenderer.render(listOf(2.0, null, 8.0))

        assertTrue(svg.contains("<svg"))
        assertTrue(svg.contains("polyline"))
        assertTrue(svg.contains("8.00 Mbps"))
    }
}
