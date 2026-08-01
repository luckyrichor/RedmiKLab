package com.redmiklab.reports

import com.redmiklab.model.EndpointClassifier
import com.redmiklab.storage.AppEvidenceEntity
import com.redmiklab.storage.AppTrafficEntity
import com.redmiklab.storage.CaptureGapEntity
import com.redmiklab.storage.ConnectionFlowEntity
import com.redmiklab.storage.NetworkLifecycleEntity
import com.redmiklab.storage.TrafficAccountingEntity

object LayeredTrafficReportBuilder {
    fun trafficSummary(
        rows: List<TrafficAccountingEntity>,
        systemMobileBytes: Long,
    ): String = buildString {
        appendLine("layer,direction,outcome,bytes")
        appendLine("ANDROID_SYSTEM_MOBILE,BIDIRECTIONAL,APPROXIMATE,$systemMobileBytes")
        rows.groupBy { Triple(it.layer, it.direction, it.outcome) }
            .toSortedMap(compareBy({ it.first }, { it.second }, { it.third }))
            .forEach { (key, values) ->
                appendLine("${csv(key.first)},${csv(key.second)},${csv(key.third)},${values.sumOf { it.bytes }}")
            }
    }

    fun systemMobileTraffic(rows: List<AppTrafficEntity>): String = buildString {
        appendLine("window_start,window_end,package_name,display_name,mobile_bytes,approximate,measurement_scope")
        rows.forEach {
            appendLine("${it.windowStartEpochMs},${it.windowEndEpochMs},${csv(it.packageName)},${csv(it.displayName)},${it.mobileBytes},${it.approximate},${csv("ANDROID_NETWORK_STATS_APPROXIMATION")}")
        }
    }

    fun tunnelObserved(rows: List<TrafficAccountingEntity>): String = accounting(
        rows.filter { it.layer == "TUNNEL_OBSERVED" },
    )

    fun forwardingOutcomes(rows: List<TrafficAccountingEntity>): String = accounting(
        rows.filter {
            it.layer in setOf(
                "TUNNEL_OBSERVED", "UPSTREAM_SOCKET_ACCEPTED",
                "DOWNSTREAM_SOCKET_RECEIVED", "FORWARDING_FAILED",
            )
        },
    )

    fun layer(rows: List<TrafficAccountingEntity>, name: String): String =
        accounting(rows.filter { it.layer == name })

    fun captureGaps(rows: List<CaptureGapEntity>): String = buildString {
        appendLine("started_at,ended_at,duration_ms,reason,system_mobile_bytes,details")
        rows.forEach {
            val duration = it.endedAtEpochMs?.let { end -> (end - it.startedAtEpochMs).coerceAtLeast(0) }
            appendLine("${it.startedAtEpochMs},${it.endedAtEpochMs ?: ""},${duration ?: ""},${csv(it.reason)},${it.systemMobileBytes},${csv(it.details ?: "")}")
        }
    }

    fun networkLifecycle(rows: List<NetworkLifecycleEntity>): String = buildString {
        appendLine("timestamp,old_state,new_state,physical_network_id,vpn_network_id,vpn_underlying_network_id,reason,retry_attempt,generation")
        rows.forEach {
            appendLine("${it.timestampEpochMs},${csv(it.oldState)},${csv(it.newState)},${csv(it.physicalNetworkId ?: "")},${csv(it.vpnNetworkId ?: "")},${csv(it.vpnUnderlyingNetworkId ?: "")},${csv(it.reason)},${it.retryAttempt},${it.generation}")
        }
    }

    fun endpointObservations(rows: List<ConnectionFlowEntity>): String = buildString {
        appendLine("timestamp,package_name,destination_address,destination_port,hostname,hostname_source,category,rule_version,confidence,reason,stage,direction,outcome,bytes")
        val classifier = EndpointClassifier()
        rows.forEach {
            val endpoint = classifier.classify(it.endpointHost)
            appendLine("${it.timestampEpochMs},${csv(it.ownerPackage ?: "")},${csv(it.destinationAddress)},${it.destinationPort},${csv(it.endpointHost ?: "")},${csv(if (it.endpointHost == null) "UNAVAILABLE" else "TLS_SNI")},${csv(endpoint.category.name)},${endpoint.ruleVersion},${csv(endpoint.confidence.name)},${csv(endpoint.reason)},${csv(it.stage)},${csv(it.direction)},${csv(it.outcome)},${it.wireBytes}")
        }
    }

    fun evidence(rows: List<AppEvidenceEntity>, prefix: String): String = buildString {
        appendLine("timestamp,package_name,display_name,event_type,event_source,event_key,content_fingerprint,metadata_availability,payload_json")
        rows.filter { it.evidenceType.startsWith(prefix) }.forEach {
            appendLine("${it.timestampEpochMs},${csv(it.packageName)},${csv(it.displayName ?: "")},${csv(it.evidenceType)},${csv(it.source)},${csv(it.eventKey ?: "")},${csv(it.contentFingerprint ?: "")},${csv(it.metadataAvailability)},${csv(it.payloadJson)}")
        }
    }

    fun behavior(rows: List<BehaviorReportRow>): String = buildString {
        appendLine("timestamp,package_name,endpoint_host,bytes,direction,stage,outcome,activity_class,media_state,media_distance_ms,endpoint_category,endpoint_rule_version,classification,confidence,reasons,counterevidence,limitation")
        rows.forEach {
            appendLine("${it.timestampEpochMs},${csv(it.packageName)},${csv(it.endpointHost ?: "")},${it.bytes},${csv(it.direction)},${csv(it.stage)},${csv(it.outcome)},${csv(it.activityClass)},${csv(it.mediaState ?: "")},${it.mediaDistanceMs ?: ""},${csv(it.endpointCategory)},${it.endpointRuleVersion},${csv(it.classification)},${csv(it.confidence)},${csv(it.reasons)},${csv(it.counterevidence)},${csv(it.limitation)}")
        }
    }

    private fun accounting(rows: List<TrafficAccountingEntity>): String = buildString {
        appendLine("window_start,window_end,package_name,stage,direction,bytes,outcome,reason")
        rows.forEach {
            appendLine("${it.windowStartEpochMs},${it.windowEndEpochMs},${csv(it.packageName ?: "")},${csv(it.layer)},${csv(it.direction)},${it.bytes},${csv(it.outcome)},${csv(it.reason ?: "")}")
        }
    }

    internal fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
