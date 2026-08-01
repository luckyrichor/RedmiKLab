package com.redmiklab.app

import com.redmiklab.model.DiagnosticRuntimeMode

class RuntimeModeUiPolicy {
    fun status(
        nextMode: DiagnosticRuntimeMode,
        activeMode: DiagnosticRuntimeMode?,
    ): String {
        val next = "下一次设置：${nextMode.label()}"
        return activeMode?.let { "本次运行：${it.label()}；$next" } ?: next
    }

    private fun DiagnosticRuntimeMode.label(): String = when (this) {
        DiagnosticRuntimeMode.STANDARD -> "标准模式"
        DiagnosticRuntimeMode.STRICT -> "严格模式"
    }
}
