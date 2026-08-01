package com.redmiklab.app

import com.redmiklab.model.DiagnosticRuntimeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeModeUiPolicyTest {
    private val policy = RuntimeModeUiPolicy()

    @Test
    fun active_run_mode_is_shown_separately_from_the_next_setting() {
        assertEquals(
            "本次运行：标准模式；下一次设置：严格模式",
            policy.status(
                nextMode = DiagnosticRuntimeMode.STRICT,
                activeMode = DiagnosticRuntimeMode.STANDARD,
            ),
        )
    }

    @Test
    fun no_active_run_shows_only_the_next_setting() {
        assertEquals(
            "下一次设置：标准模式",
            policy.status(
                nextMode = DiagnosticRuntimeMode.STANDARD,
                activeMode = null,
            ),
        )
    }
}
