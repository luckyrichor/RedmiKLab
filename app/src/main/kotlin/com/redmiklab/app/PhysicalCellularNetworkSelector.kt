package com.redmiklab.app

import com.redmiklab.diagnostics.CellularNetworkCandidateDecision
import com.redmiklab.diagnostics.CellularNetworkCandidateFacts
import com.redmiklab.diagnostics.CellularNetworkCandidatePolicy

data class PhysicalCellularNetworkCandidate<T>(
    val networkId: String,
    val value: T,
    val facts: CellularNetworkCandidateFacts,
)

data class PhysicalCellularNetworkSelection<T>(
    val selected: PhysicalCellularNetworkCandidate<T>?,
    val decisions: Map<String, CellularNetworkCandidateDecision>,
)

object PhysicalCellularNetworkSelector {
    fun <T> select(
        candidates: List<PhysicalCellularNetworkCandidate<T>>,
        activeDefaultIsValidatedVpn: Boolean,
    ): PhysicalCellularNetworkSelection<T> {
        val evaluated = candidates.map { candidate ->
            candidate to CellularNetworkCandidatePolicy.evaluate(
                candidate.facts,
                activeDefaultIsValidatedVpn,
            )
        }
        return PhysicalCellularNetworkSelection(
            selected = evaluated
                .filter { (_, decision) -> decision.score != null }
                .maxByOrNull { (_, decision) -> requireNotNull(decision.score) }
                ?.first,
            decisions = evaluated.associate { (candidate, decision) -> candidate.networkId to decision },
        )
    }
}
