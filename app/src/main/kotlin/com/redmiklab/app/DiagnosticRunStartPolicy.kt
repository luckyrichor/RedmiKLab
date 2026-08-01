package com.redmiklab.app

import com.redmiklab.model.DiagnosticConfig
import com.redmiklab.model.DiagnosticRuntimeMode

enum class DiagnosticRunStartDecision {
    REJECT,
    START_READY,
    START_WITH_TUNNEL_GAP,
}

/** Separates permanent configuration errors from a transient VPN tunnel outage. */
class DiagnosticRunStartPolicy {
    fun decide(
        config: DiagnosticConfig,
        connectionCaptureEnabled: Boolean,
        connectionCaptureRunning: Boolean,
    ): DiagnosticRunStartDecision {
        val validation = config.validate() + config.validateRuntime(connectionCaptureEnabled)
        if (validation.isNotEmpty()) return DiagnosticRunStartDecision.REJECT
        return if (config.runtimeMode == DiagnosticRuntimeMode.STRICT && !connectionCaptureRunning) {
            DiagnosticRunStartDecision.START_WITH_TUNNEL_GAP
        } else {
            DiagnosticRunStartDecision.START_READY
        }
    }
}
