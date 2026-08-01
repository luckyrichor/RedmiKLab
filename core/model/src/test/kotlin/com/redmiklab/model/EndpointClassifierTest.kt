package com.redmiklab.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointClassifierTest {
    private val classifier = EndpointClassifier()

    @Test
    fun classifies_known_video_cdn_domains() {
        val result = classifier.classify("v3-dy-o.zjcdn.com")

        assertEquals(EndpointCategory.VIDEO_CDN, result.category)
        assertEquals("DOMAIN_VIDEO_CDN_PATTERN", result.reason)
    }

    @Test
    fun classifies_advertising_and_analytics_domains_separately() {
        assertEquals(
            EndpointCategory.ADVERTISING,
            classifier.classify("ads.example.com").category,
        )
        assertEquals(
            EndpointCategory.ANALYTICS,
            classifier.classify("analytics.example.com").category,
        )
    }

    @Test
    fun missing_hostname_remains_unknown() {
        assertEquals(EndpointCategory.UNKNOWN, classifier.classify(null).category)
    }

    @Test
    fun classifies_audio_image_log_live_and_ecommerce_domains() {
        assertEquals(EndpointCategory.AUDIO_CDN, classifier.classify("audio-cdn.example.com").category)
        assertEquals(EndpointCategory.IMAGE, classifier.classify("img.example.com").category)
        assertEquals(EndpointCategory.LOG_UPLOAD, classifier.classify("log-upload.example.com").category)
        assertEquals(EndpointCategory.LIVE, classifier.classify("live-stream.example.com").category)
        assertEquals(EndpointCategory.ECOMMERCE, classifier.classify("shop.example.com").category)
    }

    @Test
    fun classification_exposes_version_and_confidence() {
        val result = classifier.classify("v3-dy-o.zjcdn.com")

        assertEquals(2, result.ruleVersion)
        assertEquals(EvidenceConfidence.HIGH, result.confidence)
    }
}
