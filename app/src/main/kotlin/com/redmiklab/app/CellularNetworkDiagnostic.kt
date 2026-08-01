package com.redmiklab.app

data class CellularNetworkCandidateSnapshot(
    val networkId: String,
    val isActive: Boolean,
    val transports: List<String>,
    val hasInternet: Boolean,
    val isValidated: Boolean,
    val isNotSuspended: Boolean,
    val isNotRestricted: Boolean,
    val isMetered: Boolean,
    val isRecordedUnderlying: Boolean,
    val score: Int?,
    val rejectionReason: String?,
    val interfaceName: String?,
    val dnsServers: List<String>,
)

data class CellularSystemNetworkState(
    val activeNetworkId: String?,
    val activeNetworkMetered: Boolean,
    val restrictBackgroundStatus: Int,
    val networks: List<CellularNetworkCandidateSnapshot>,
    val recordedUnderlyingNetworkId: String? = null,
)

enum class CellularResolutionOutcome {
    CURRENT_CANDIDATE,
    REQUEST_RESOLVED,
    NO_SAFE_PHYSICAL_CELLULAR,
    REQUEST_TIMEOUT,
    REQUEST_UNAVAILABLE,
    REQUEST_SECURITY_ERROR,
    REQUEST_RUNTIME_ERROR,
}

data class CellularNetworkDiagnostic(
    val outcome: CellularResolutionOutcome,
    val selectedNetworkId: String?,
    val initialState: CellularSystemNetworkState,
    val finalState: CellularSystemNetworkState,
    val resolutionWaitMs: Long,
    val errorType: String? = null,
) {
    fun toJson(): String = buildString {
        append('{')
        field("outcome", outcome.name)
        append(',')
        nullableField("selectedNetworkId", selectedNetworkId)
        append(",\"initialState\":")
        state(initialState)
        append(",\"finalState\":")
        state(finalState)
        append(",\"resolutionWaitMs\":$resolutionWaitMs")
        append(',')
        nullableField("errorType", errorType)
        append('}')
    }

    private fun StringBuilder.state(value: CellularSystemNetworkState) {
        append('{')
        nullableField("activeNetworkId", value.activeNetworkId)
        append(",\"activeNetworkMetered\":${value.activeNetworkMetered}")
        append(",\"restrictBackgroundStatus\":${value.restrictBackgroundStatus}")
        append(',')
        nullableField("recordedUnderlyingNetworkId", value.recordedUnderlyingNetworkId)
        append(",\"networks\":[")
        value.networks.forEachIndexed { index, network ->
            if (index > 0) append(',')
            candidate(network)
        }
        append("]}")
    }

    private fun StringBuilder.candidate(value: CellularNetworkCandidateSnapshot) {
        append('{')
        field("networkId", value.networkId)
        append(",\"isActive\":${value.isActive}")
        append(",\"transports\":[")
        value.transports.forEachIndexed { index, transport ->
            if (index > 0) append(',')
            string(transport)
        }
        append(']')
        append(",\"hasInternet\":${value.hasInternet}")
        append(",\"isValidated\":${value.isValidated}")
        append(",\"isNotSuspended\":${value.isNotSuspended}")
        append(",\"isNotRestricted\":${value.isNotRestricted}")
        append(",\"isMetered\":${value.isMetered}")
        append(",\"isRecordedUnderlying\":${value.isRecordedUnderlying}")
        append(",\"score\":${value.score ?: "null"}")
        append(',')
        nullableField("rejectionReason", value.rejectionReason)
        append(',')
        nullableField("interfaceName", value.interfaceName)
        append(",\"dnsServers\":[")
        value.dnsServers.forEachIndexed { index, server ->
            if (index > 0) append(',')
            string(server)
        }
        append("]}")
    }

    private fun StringBuilder.field(name: String, value: String) {
        string(name)
        append(':')
        string(value)
    }

    private fun StringBuilder.nullableField(name: String, value: String?) {
        string(name)
        append(':')
        if (value == null) append("null") else string(value)
    }

    private fun StringBuilder.string(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
}
