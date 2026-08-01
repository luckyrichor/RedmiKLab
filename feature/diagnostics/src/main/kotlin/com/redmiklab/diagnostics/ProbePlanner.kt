package com.redmiklab.diagnostics

data class ProbePlan(val connectivity: Boolean, val throughput: Boolean)

object ProbePlanner {
    fun plan(throughputAllowed: Boolean): ProbePlan = ProbePlan(
        connectivity = true,
        throughput = throughputAllowed,
    )
}
