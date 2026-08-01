package com.redmiklab.model

enum class EndpointCategory {
    VIDEO_CDN,
    AUDIO_CDN,
    IMAGE,
    ADVERTISING,
    ANALYTICS,
    LOG_UPLOAD,
    API,
    PUSH,
    LIVE,
    ECOMMERCE,
    OTHER,
    UNKNOWN,
}

data class EndpointClassification(
    val category: EndpointCategory,
    val reason: String,
    val ruleVersion: Int = 2,
    val confidence: EvidenceConfidence = EvidenceConfidence.MEDIUM,
)

class EndpointClassifier {
    fun classify(hostname: String?): EndpointClassification {
        val host = hostname?.trim()?.lowercase()?.trimEnd('.')
            ?: return EndpointClassification(EndpointCategory.UNKNOWN, "HOSTNAME_UNAVAILABLE")
        if (host.isEmpty()) return EndpointClassification(EndpointCategory.UNKNOWN, "HOSTNAME_UNAVAILABLE")
        if (host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) || ':' in host) {
            return EndpointClassification(EndpointCategory.UNKNOWN, "HOSTNAME_UNAVAILABLE")
        }
        val labels = host.split('.', '-', '_')
        return when {
            labels.any { it == "ads" || it == "ad" } ||
                listOf("pangle", "pangolin", "oceanengine").any(host::contains) ->
                EndpointClassification(EndpointCategory.ADVERTISING, "DOMAIN_ADVERTISING_PATTERN")
            labels.any { it in ANALYTICS_LABELS } || "applog" in host ->
                EndpointClassification(EndpointCategory.ANALYTICS, "DOMAIN_ANALYTICS_PATTERN")
            labels.any { it in LOG_LABELS } || "log-upload" in host ->
                EndpointClassification(EndpointCategory.LOG_UPLOAD, "DOMAIN_LOG_UPLOAD_PATTERN")
            VIDEO_SUFFIXES.any(host::endsWith) || "douyinvod" in host ->
                EndpointClassification(EndpointCategory.VIDEO_CDN, "DOMAIN_VIDEO_CDN_PATTERN", confidence = EvidenceConfidence.HIGH)
            labels.any { it in AUDIO_LABELS } ->
                EndpointClassification(EndpointCategory.AUDIO_CDN, "DOMAIN_AUDIO_PATTERN")
            labels.any { it in IMAGE_LABELS } ->
                EndpointClassification(EndpointCategory.IMAGE, "DOMAIN_IMAGE_PATTERN")
            "mipush" in host || labels.any { it == "push" } ->
                EndpointClassification(EndpointCategory.PUSH, "DOMAIN_PUSH_PATTERN")
            labels.any { it in LIVE_LABELS } ->
                EndpointClassification(EndpointCategory.LIVE, "DOMAIN_LIVE_PATTERN")
            labels.any { it in ECOMMERCE_LABELS } ->
                EndpointClassification(EndpointCategory.ECOMMERCE, "DOMAIN_ECOMMERCE_PATTERN")
            labels.firstOrNull() == "api" || listOf("snssdk", "amemv").any(host::contains) ->
                EndpointClassification(EndpointCategory.API, "DOMAIN_API_PATTERN")
            else -> EndpointClassification(EndpointCategory.OTHER, "NO_KNOWN_PATTERN")
        }
    }

    private companion object {
        val VIDEO_SUFFIXES = listOf(
            "zjcdn.com",
            "bytecdn.com",
            "bytecdn.cn",
            "bilivideo.com",
            "akamaized.net",
        )
        val ANALYTICS_LABELS = setOf("analytics", "stat", "stats", "tracker", "tracking", "telemetry")
        val AUDIO_LABELS = setOf("audio", "music", "voice", "podcast")
        val IMAGE_LABELS = setOf("img", "image", "images", "avatar", "thumb", "thumbnail")
        val LOG_LABELS = setOf("log", "logs", "logging", "crash")
        val LIVE_LABELS = setOf("live", "livestream", "streaming")
        val ECOMMERCE_LABELS = setOf("shop", "shopping", "mall", "ecommerce", "ecom")
    }
}
