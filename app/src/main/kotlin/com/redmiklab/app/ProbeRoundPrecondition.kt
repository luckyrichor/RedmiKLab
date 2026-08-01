package com.redmiklab.app

data class ProbeRoundPreconditionDecision(
    val shouldAttemptEndpoints: Boolean,
    val failure: String?,
)

object ProbeRoundPrecondition {
    fun evaluate(hasPhysicalCellularNetwork: Boolean) = if (hasPhysicalCellularNetwork) {
        ProbeRoundPreconditionDecision(true, null)
    } else {
        ProbeRoundPreconditionDecision(false, "NO_PHYSICAL_CELLULAR_NETWORK")
    }
}
