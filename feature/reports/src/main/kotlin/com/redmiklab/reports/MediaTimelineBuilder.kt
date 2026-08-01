package com.redmiklab.reports

import com.redmiklab.model.BehaviorEvidence
import com.redmiklab.model.BehaviorEvidenceClassifier
import com.redmiklab.model.EndpointCategory
import com.redmiklab.model.EndpointClassifier
import com.redmiklab.model.MediaPlaybackState
import com.redmiklab.storage.AppEvidenceEntity
import com.redmiklab.storage.ConnectionFlowEntity
import kotlin.math.abs

data class BehaviorReportRow(
    val timestampEpochMs: Long,
    val packageName: String,
    val endpointHost: String?,
    val bytes: Long,
    val direction: String,
    val stage: String,
    val outcome: String,
    val activityClass: String,
    val mediaState: String?,
    val mediaDistanceMs: Long?,
    val endpointCategory: String,
    val endpointRuleVersion: Int,
    val classification: String,
    val confidence: String,
    val reasons: String,
    val counterevidence: String,
    val limitation: String,
)

object MediaTimelineBuilder {
    fun build(
        flows: List<ConnectionFlowEntity>,
        evidence: List<AppEvidenceEntity>,
    ): List<BehaviorReportRow> {
        val mediaByPackage = evidence.filter { it.evidenceType.startsWith("MEDIA_") }
            .groupBy { it.packageName }
        val endpointClassifier = EndpointClassifier()
        val behaviorClassifier = BehaviorEvidenceClassifier()
        val rows = flows.map { flow ->
            val packageName = flow.ownerPackage ?: "UNATTRIBUTED"
            val media = mediaByPackage[packageName]
                ?.filter { it.timestampEpochMs <= flow.timestampEpochMs }
                ?.maxByOrNull { it.timestampEpochMs }
            val mediaState = media?.payloadJson?.let(::mediaState)
            val mediaDistance = media?.let { abs(flow.timestampEpochMs - it.timestampEpochMs) }
            val endpoint = endpointClassifier.classify(flow.endpointHost)
            val result = behaviorClassifier.classify(
                BehaviorEvidence(
                    screenLocked = flow.activityClass == "BACKGROUND_SCREEN_LOCKED",
                    appInForeground = flow.activityClass == "FOREGROUND_ACTIVE",
                    mediaState = mediaState,
                    endpointCategory = endpoint.category,
                    windowBytes = flow.wireBytes,
                    mediaPermissionAvailable = media?.metadataAvailability != "PERMISSION_UNAVAILABLE" && media != null,
                    upstreamBytes = if (flow.direction == "UP") flow.wireBytes else 0,
                    downstreamBytes = if (flow.direction == "DOWN") flow.wireBytes else 0,
                    forwardingOutcome = flow.outcome,
                    mediaTimestampDistanceMs = mediaDistance,
                    endpointRuleVersion = endpoint.ruleVersion,
                ),
            )
            BehaviorReportRow(
                flow.timestampEpochMs, packageName, flow.endpointHost, flow.wireBytes,
                flow.direction, flow.stage, flow.outcome, flow.activityClass, mediaState?.name,
                mediaDistance, endpoint.category.name, endpoint.ruleVersion,
                result.classification.name, result.confidence.name, result.reasons.joinToString("|"),
                counterevidence(flow, mediaState),
                "NO_HTTPS_PAYLOAD;CORRELATION_NOT_CAUSATION",
            )
        }.toMutableList()
        val packagesWithFlows = flows.mapNotNull { it.ownerPackage }.toSet()
        mediaByPackage.filterKeys { it !in packagesWithFlows }.forEach { (packageName, mediaRows) ->
            val media = mediaRows.maxBy { it.timestampEpochMs }
            val state = mediaState(media.payloadJson)
            val result = behaviorClassifier.classify(
                BehaviorEvidence(false, false, state, EndpointCategory.UNKNOWN, 0, true),
            )
            rows += BehaviorReportRow(
                media.timestampEpochMs, packageName, null, 0, "NONE", "MEDIA_ONLY", "OBSERVED",
                "MEDIA_ONLY", state?.name, 0, "UNKNOWN", 2,
                result.classification.name, result.confidence.name, result.reasons.joinToString("|"),
                "NO_NETWORK_FLOW", "MEDIA_STATE_ONLY",
            )
        }
        return rows.sortedBy { it.timestampEpochMs }
    }

    private fun counterevidence(flow: ConnectionFlowEntity, state: MediaPlaybackState?): String = buildList {
        if (flow.endpointHost == null) add("HOSTNAME_UNAVAILABLE")
        if (state == null) add("MEDIA_STATE_UNAVAILABLE")
        if (flow.outcome == "OBSERVED") add("REMOTE_FORWARDING_NOT_CONFIRMED")
    }.joinToString("|")

    private fun mediaState(payloadJson: String): MediaPlaybackState? {
        val value = Regex("\"playbackState\"\\s*:\\s*(-?\\d+)").find(payloadJson)
            ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        return when (value) {
            3 -> MediaPlaybackState.PLAYING
            6, 8 -> MediaPlaybackState.BUFFERING
            2 -> MediaPlaybackState.PAUSED
            0, 1, 7 -> MediaPlaybackState.STOPPED
            else -> MediaPlaybackState.OTHER
        }
    }
}
