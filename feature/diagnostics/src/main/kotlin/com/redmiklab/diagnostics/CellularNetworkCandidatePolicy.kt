package com.redmiklab.diagnostics

data class CellularNetworkCandidateFacts(
    val isCellular: Boolean,
    val isVpn: Boolean,
    val isIms: Boolean,
    val isEims: Boolean,
    val isMms: Boolean,
    val hasInternet: Boolean,
    val isValidated: Boolean,
    val isNotSuspended: Boolean,
    val isRecordedUnderlying: Boolean,
)

enum class CellularCandidateRejection {
    NOT_CELLULAR,
    VPN_TRANSPORT,
    IMS_CAPABILITY,
    EIMS_CAPABILITY,
    MMS_CAPABILITY,
    NOT_VALIDATED,
    SUSPENDED,
    INTERNET_MISSING_UNTRUSTED,
}

data class CellularNetworkCandidateDecision(
    val score: Int?,
    val rejection: CellularCandidateRejection?,
)

object CellularNetworkCandidatePolicy {
    fun evaluate(
        facts: CellularNetworkCandidateFacts,
        activeDefaultIsValidatedVpn: Boolean,
    ): CellularNetworkCandidateDecision {
        val rejection = when {
            !facts.isCellular -> CellularCandidateRejection.NOT_CELLULAR
            facts.isVpn -> CellularCandidateRejection.VPN_TRANSPORT
            facts.isIms -> CellularCandidateRejection.IMS_CAPABILITY
            facts.isEims -> CellularCandidateRejection.EIMS_CAPABILITY
            facts.isMms -> CellularCandidateRejection.MMS_CAPABILITY
            !facts.isNotSuspended -> CellularCandidateRejection.SUSPENDED
            !facts.hasInternet && !(activeDefaultIsValidatedVpn && facts.isRecordedUnderlying) ->
                CellularCandidateRejection.INTERNET_MISSING_UNTRUSTED
            else -> null
        }
        if (rejection != null) return CellularNetworkCandidateDecision(null, rejection)
        val score = if (facts.hasInternet) NORMAL_INTERNET_SCORE else VPN_COMPATIBILITY_SCORE
        return CellularNetworkCandidateDecision(
            score = score +
                (if (facts.isValidated) VALIDATED_BONUS else 0) +
                (if (facts.isRecordedUnderlying) RECORDED_UNDERLYING_BONUS else 0),
            rejection = null,
        )
    }

    private const val NORMAL_INTERNET_SCORE = 20
    private const val VPN_COMPATIBILITY_SCORE = 10
    private const val VALIDATED_BONUS = 8
    private const val RECORDED_UNDERLYING_BONUS = 4
}
