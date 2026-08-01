package com.redmiklab.app

data class CapturedConnectionFlow(
    val timestampEpochMs: Long,
    val protocol: Int,
    val sourceAddress: String,
    val sourcePort: Int,
    val destinationAddress: String,
    val destinationPort: Int,
    val wireBytes: Long,
    val ownerPackage: String?,
    val ownerLabel: String?,
    val activityClass: String,
    val hostname: String? = null,
    val direction: String = "UP",
    val stage: String = "TUNNEL_OBSERVED",
    val outcome: String = "OBSERVED",
    val reason: String? = null,
)

data class AggregatedConnectionFlow(
    val timestampEpochMs: Long,
    val protocol: Int,
    val sourceAddress: String,
    val sourcePort: Int,
    val destinationAddress: String,
    val destinationPort: Int,
    val wireBytes: Long,
    val ownerPackage: String?,
    val ownerLabel: String?,
    val activityClass: String,
    val hostname: String? = null,
    val direction: String = "UP",
    val stage: String = "TUNNEL_OBSERVED",
    val outcome: String = "OBSERVED",
    val reason: String? = null,
)

class ConnectionFlowAccumulator {
    private data class Key(
        val protocol: Int,
        val sourceAddress: String,
        val sourcePort: Int,
        val destinationAddress: String,
        val destinationPort: Int,
        val hostname: String?,
        val ownerPackage: String?,
        val ownerLabel: String?,
        val activityClass: String,
        val direction: String,
        val stage: String,
        val outcome: String,
        val reason: String?,
    )

    private data class Value(var timestampEpochMs: Long, var wireBytes: Long)

    private val values = linkedMapOf<Key, Value>()

    @Synchronized
    fun add(flow: CapturedConnectionFlow) {
        if (flow.wireBytes < 0 || (flow.wireBytes == 0L && flow.outcome != "FAILED")) return
        val key = Key(
            flow.protocol,
            flow.sourceAddress,
            flow.sourcePort,
            flow.destinationAddress,
            flow.destinationPort,
            flow.hostname,
            flow.ownerPackage,
            flow.ownerLabel,
            flow.activityClass,
            flow.direction,
            flow.stage,
            flow.outcome,
            flow.reason,
        )
        val value = values.getOrPut(key) { Value(flow.timestampEpochMs, 0) }
        value.timestampEpochMs = maxOf(value.timestampEpochMs, flow.timestampEpochMs)
        value.wireBytes += flow.wireBytes
    }

    @Synchronized
    fun drain(): List<AggregatedConnectionFlow> {
        val result = values.map { (key, value) ->
            AggregatedConnectionFlow(
                value.timestampEpochMs,
                key.protocol,
                key.sourceAddress,
                key.sourcePort,
                key.destinationAddress,
                key.destinationPort,
                value.wireBytes,
                key.ownerPackage,
                key.ownerLabel,
                key.activityClass,
                key.hostname,
                key.direction,
                key.stage,
                key.outcome,
                key.reason,
            )
        }
        values.clear()
        return result
    }
}
