package com.redmiklab.model

enum class MediaPlaybackState {
    PLAYING,
    PAUSED,
    BUFFERING,
    STOPPED,
    OTHER,
}

enum class BehaviorClassification {
    LIKELY_FOREGROUND_PLAYBACK,
    LIKELY_BACKGROUND_PLAYBACK,
    LIKELY_CACHED_PLAYBACK,
    POSSIBLE_PRELOAD,
    POSSIBLE_ADVERTISING,
    POSSIBLE_ANALYTICS,
    POSSIBLE_LOG_UPLOAD,
    UNEXPLAINED_BACKGROUND_UPLOAD,
    UNCLASSIFIED,
}

enum class EvidenceConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

data class BehaviorEvidence(
    val screenLocked: Boolean,
    val appInForeground: Boolean,
    val mediaState: MediaPlaybackState?,
    val endpointCategory: EndpointCategory,
    val windowBytes: Long,
    val mediaPermissionAvailable: Boolean,
    val upstreamBytes: Long = 0,
    val downstreamBytes: Long = windowBytes,
    val forwardingOutcome: String = "UNKNOWN",
    val mediaTimestampDistanceMs: Long? = null,
    val endpointRuleVersion: Int = 2,
    val excessBurstEvidence: Boolean = false,
)

data class BehaviorClassificationResult(
    val classification: BehaviorClassification,
    val confidence: EvidenceConfidence,
    val reasons: List<String>,
)

class BehaviorEvidenceClassifier {
    fun classify(evidence: BehaviorEvidence): BehaviorClassificationResult {
        if (evidence.endpointCategory == EndpointCategory.ADVERTISING) {
            return result(BehaviorClassification.POSSIBLE_ADVERTISING, EvidenceConfidence.MEDIUM, "AD_ENDPOINT")
        }
        if (evidence.endpointCategory == EndpointCategory.ANALYTICS) {
            return result(BehaviorClassification.POSSIBLE_ANALYTICS, EvidenceConfidence.MEDIUM, "ANALYTICS_ENDPOINT")
        }
        if (evidence.endpointCategory == EndpointCategory.LOG_UPLOAD && evidence.upstreamBytes > 0) {
            return result(
                BehaviorClassification.POSSIBLE_LOG_UPLOAD,
                EvidenceConfidence.MEDIUM,
                "LOG_UPLOAD_ENDPOINT",
                "UPSTREAM_BYTES_PRESENT",
                "UPLOAD_CONTENT_UNKNOWN",
            )
        }
        if (!evidence.appInForeground && evidence.upstreamBytes >= BACKGROUND_UPLOAD_BYTES &&
            evidence.forwardingOutcome == "ACCEPTED"
        ) {
            return result(
                BehaviorClassification.UNEXPLAINED_BACKGROUND_UPLOAD,
                EvidenceConfidence.LOW,
                if (evidence.screenLocked) "SCREEN_LOCKED" else "APP_BACKGROUND",
                "LARGE_UPSTREAM_TRANSFER",
                "UPLOAD_CONTENT_UNKNOWN",
            )
        }
        if (!evidence.mediaPermissionAvailable) {
            return result(
                BehaviorClassification.UNCLASSIFIED,
                EvidenceConfidence.LOW,
                "MEDIA_PERMISSION_UNAVAILABLE",
                evidence.endpointCategory.reasonCode(),
            )
        }
        val mediaStateIsFresh = evidence.mediaTimestampDistanceMs == null ||
            evidence.mediaTimestampDistanceMs <= MAX_MEDIA_EVIDENCE_AGE_MS
        if (!mediaStateIsFresh) {
            return result(
                BehaviorClassification.UNCLASSIFIED,
                EvidenceConfidence.LOW,
                "MEDIA_STATE_STALE",
                evidence.endpointCategory.reasonCode(),
            )
        }
        if (evidence.mediaState == MediaPlaybackState.PLAYING &&
            evidence.endpointCategory == EndpointCategory.VIDEO_CDN
        ) {
            return when {
                evidence.screenLocked -> result(
                    BehaviorClassification.LIKELY_BACKGROUND_PLAYBACK,
                    EvidenceConfidence.HIGH,
                    "SCREEN_LOCKED",
                    "MEDIA_PLAYING",
                    "VIDEO_CDN",
                )
                evidence.appInForeground -> result(
                    BehaviorClassification.LIKELY_FOREGROUND_PLAYBACK,
                    EvidenceConfidence.HIGH,
                    "APP_FOREGROUND",
                    "MEDIA_PLAYING",
                    "VIDEO_CDN",
                )
                else -> result(
                    BehaviorClassification.LIKELY_BACKGROUND_PLAYBACK,
                    EvidenceConfidence.HIGH,
                    "APP_BACKGROUND",
                    "MEDIA_PLAYING",
                    "VIDEO_CDN",
                )
            }
        }
        if (evidence.mediaState in setOf(MediaPlaybackState.PAUSED, MediaPlaybackState.STOPPED) &&
            evidence.endpointCategory == EndpointCategory.VIDEO_CDN &&
            evidence.windowBytes >= PRELOAD_BURST_BYTES &&
            (evidence.excessBurstEvidence || evidence.downstreamBytes >= PRELOAD_BURST_BYTES)
        ) {
            return result(
                BehaviorClassification.POSSIBLE_PRELOAD,
                EvidenceConfidence.MEDIUM,
                "MEDIA_NOT_PLAYING",
                "VIDEO_CDN_BURST",
            )
        }
        if (evidence.mediaState == MediaPlaybackState.PLAYING && evidence.windowBytes == 0L) {
            return result(
                BehaviorClassification.LIKELY_CACHED_PLAYBACK,
                EvidenceConfidence.HIGH,
                "MEDIA_PLAYING",
                "NO_NETWORK_BYTES",
            )
        }
        return result(BehaviorClassification.UNCLASSIFIED, EvidenceConfidence.LOW, "INSUFFICIENT_EVIDENCE")
    }

    private fun EndpointCategory.reasonCode(): String = "ENDPOINT_$name"

    private fun result(
        classification: BehaviorClassification,
        confidence: EvidenceConfidence,
        vararg reasons: String,
    ) = BehaviorClassificationResult(classification, confidence, reasons.toList())

    private companion object {
        const val PRELOAD_BURST_BYTES = 5_000_000L
        const val BACKGROUND_UPLOAD_BYTES = 5_000_000L
        const val MAX_MEDIA_EVIDENCE_AGE_MS = 5 * 60_000L
    }
}
