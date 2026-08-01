package com.redmiklab.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorEvidenceClassifierTest {
    private val classifier = BehaviorEvidenceClassifier()

    @Test
    fun locked_playing_video_cdn_is_likely_background_playback() {
        val result = classifier.classify(
            BehaviorEvidence(
                screenLocked = true,
                appInForeground = false,
                mediaState = MediaPlaybackState.PLAYING,
                endpointCategory = EndpointCategory.VIDEO_CDN,
                windowBytes = 8_000_000,
                mediaPermissionAvailable = true,
            ),
        )

        assertEquals(BehaviorClassification.LIKELY_BACKGROUND_PLAYBACK, result.classification)
        assertEquals(EvidenceConfidence.HIGH, result.confidence)
        assertTrue("SCREEN_LOCKED" in result.reasons)
    }

    @Test
    fun paused_video_cdn_burst_is_possible_preload() {
        val result = classifier.classify(
            BehaviorEvidence(
                screenLocked = false,
                appInForeground = false,
                mediaState = MediaPlaybackState.PAUSED,
                endpointCategory = EndpointCategory.VIDEO_CDN,
                windowBytes = 8_000_000,
                mediaPermissionAvailable = true,
            ),
        )

        assertEquals(BehaviorClassification.POSSIBLE_PRELOAD, result.classification)
    }

    @Test
    fun missing_media_permission_caps_playback_confidence() {
        val result = classifier.classify(
            BehaviorEvidence(
                screenLocked = true,
                appInForeground = false,
                mediaState = null,
                endpointCategory = EndpointCategory.VIDEO_CDN,
                windowBytes = 8_000_000,
                mediaPermissionAvailable = false,
            ),
        )

        assertEquals(EvidenceConfidence.LOW, result.confidence)
        assertTrue("MEDIA_PERMISSION_UNAVAILABLE" in result.reasons)
    }

    @Test
    fun large_locked_background_upload_to_unknown_endpoint_is_flagged_without_claiming_content() {
        val result = classifier.classify(
            BehaviorEvidence(
                screenLocked = true,
                appInForeground = false,
                mediaState = null,
                endpointCategory = EndpointCategory.UNKNOWN,
                windowBytes = 12_020_000,
                mediaPermissionAvailable = true,
                upstreamBytes = 12_000_000,
                downstreamBytes = 20_000,
                forwardingOutcome = "ACCEPTED",
            ),
        )

        assertEquals(BehaviorClassification.UNEXPLAINED_BACKGROUND_UPLOAD, result.classification)
        assertTrue("UPLOAD_CONTENT_UNKNOWN" in result.reasons)
    }

    @Test
    fun log_endpoint_with_upstream_bytes_is_possible_log_upload() {
        val result = classifier.classify(
            BehaviorEvidence(
                false, false, null, EndpointCategory.LOG_UPLOAD, 2_000_000, true,
                upstreamBytes = 2_000_000,
                downstreamBytes = 0,
                forwardingOutcome = "ACCEPTED",
            ),
        )

        assertEquals(BehaviorClassification.POSSIBLE_LOG_UPLOAD, result.classification)
    }

    @Test
    fun stale_playback_state_does_not_create_high_confidence_playback_claim() {
        val result = classifier.classify(
            BehaviorEvidence(
                false, true, MediaPlaybackState.PLAYING, EndpointCategory.VIDEO_CDN, 8_000_000, true,
                mediaTimestampDistanceMs = 20 * 60_000L,
            ),
        )

        assertEquals(BehaviorClassification.UNCLASSIFIED, result.classification)
        assertTrue("MEDIA_STATE_STALE" in result.reasons)
    }
}
