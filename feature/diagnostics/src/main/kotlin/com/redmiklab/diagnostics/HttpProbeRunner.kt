package com.redmiklab.diagnostics

data class ProbeRequest(val url: String, val reservedBytes: Long)
data class ProbeExecutionResult(val outcome: ProbeOutcome)
enum class ProbeOutcome { Success, BudgetExhausted, ConnectionFailed }

fun interface ProbeConnectionFactory { fun open(url: String) }

object FailingConnectionFactory : ProbeConnectionFactory {
    override fun open(url: String) = error("Connection should not be opened")
}

class HttpProbeRunner(private val connectionFactory: ProbeConnectionFactory) {
    fun run(request: ProbeRequest, budget: BudgetTracker): ProbeExecutionResult {
        if (!budget.reserve(request.reservedBytes)) return ProbeExecutionResult(ProbeOutcome.BudgetExhausted)
        return runCatching { connectionFactory.open(request.url); ProbeExecutionResult(ProbeOutcome.Success) }
            .getOrElse { ProbeExecutionResult(ProbeOutcome.ConnectionFailed) }
    }
}
