package com.redmiklab.app

import com.redmiklab.model.DiagnosticConfig
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticTimeEditPolicyTest {
    @Test
    fun allows_an_invalid_legacy_window_to_be_adjusted_in_multiple_steps() {
        val legacy = DiagnosticConfig.default().copy(
            start = LocalTime.MIDNIGHT,
            end = LocalTime.of(7, 0),
        )
        val firstAdjustment = legacy.copy(start = LocalTime.of(18, 15))

        assertTrue(DiagnosticTimeEditPolicy.canSave(legacy, firstAdjustment))
    }

    @Test
    fun rejects_a_new_over_six_hour_window_when_the_current_window_is_valid() {
        val current = DiagnosticConfig.default()
        val invalidUpdate = current.copy(end = LocalTime.of(7, 0))

        assertFalse(DiagnosticTimeEditPolicy.canSave(current, invalidUpdate))
    }

    @Test
    fun accepts_a_valid_time_update() {
        val current = DiagnosticConfig.default()
        val validUpdate = current.copy(start = LocalTime.of(1, 0))

        assertTrue(DiagnosticTimeEditPolicy.canSave(current, validUpdate))
    }
}
