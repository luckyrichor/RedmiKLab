package com.redmiklab.app

import com.redmiklab.reports.ReportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportUiPoliciesTest {
    @Test
    fun retry_delay_units_normalize_to_the_existing_seconds_setting() {
        assertEquals(10, RetryDelayUnit.SECONDS.toSeconds(10))
        assertEquals(120, RetryDelayUnit.MINUTES.toSeconds(2))
        assertEquals(3_600, RetryDelayUnit.HOURS.toSeconds(1))
        assertEquals(RetryDelayValue(2, RetryDelayUnit.MINUTES), RetryDelayValue.fromSeconds(120))
    }

    @Test
    fun report_names_have_one_selected_suffix_and_remove_illegal_characters() {
        assertEquals(
            "夜间_测试.json",
            ReportFileNamePolicy.withFormat(" 夜间/测试.zip ", ReportFormat.JSON),
        )
        assertEquals("移动网络测试", ReportFileNamePolicy.baseName("移动网络测试.pdf"))
    }

    @Test
    fun process_feedback_gate_only_allows_once_until_a_new_process_is_created() {
        val firstProcess = ProcessFeedbackGate()
        assertTrue(firstProcess.take())
        assertFalse(firstProcess.take())

        assertTrue(ProcessFeedbackGate().take())
    }
}
