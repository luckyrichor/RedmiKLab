package com.redmiklab.diagnostics

import com.redmiklab.model.DiagnosticConfig
import com.redmiklab.model.DiagnosticRuntimeMode

data class LockedRunConfig(
    val mode: DiagnosticRuntimeMode,
    val snapshotMinutes: Int,
    val connectivityMinutes: Int,
    val throughputMinutes: Int,
    val connectionCaptureEnabled: Boolean,
)

object RunConfigurationLock {
    fun lock(config: DiagnosticConfig, connectionCaptureEnabled: Boolean): LockedRunConfig =
        LockedRunConfig(
            mode = config.runtimeMode,
            snapshotMinutes = config.snapshotMinutes,
            connectivityMinutes = config.connectivityMinutes,
            throughputMinutes = config.throughputMinutes,
            connectionCaptureEnabled = connectionCaptureEnabled,
        )
}
