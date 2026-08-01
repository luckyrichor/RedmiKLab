package com.redmiklab.app

import java.util.concurrent.Executor

data class NotificationEvidenceRecord(
    val packageName: String,
    val evidenceType: String,
    val payloadJson: String,
    val timestampEpochMs: Long,
    val eventKey: String? = null,
    val contentFingerprint: String? = null,
    val metadataAvailability: String = "PROVIDED",
    val runId: String? = null,
)

class NotificationEvidenceDispatcher(
    private val executor: Executor,
    private val persist: (NotificationEvidenceRecord) -> Unit,
) {
    private val latestFingerprint = mutableMapOf<Triple<String?, String?, String>, Pair<String, Long>>()

    fun dispatch(produce: () -> NotificationEvidenceRecord) {
        executor.execute {
            runCatching(produce)
                .getOrNull()
                ?.let { evidence ->
                    val fingerprint = evidence.contentFingerprint ?: MediaEvidenceFingerprint.sha256(
                        evidence.packageName,
                        evidence.evidenceType,
                        evidence.eventKey,
                        evidence.payloadJson,
                    )
                    val key = Triple(evidence.runId, evidence.eventKey, evidence.evidenceType)
                    val previous = latestFingerprint[key]
                    if (previous?.first == fingerprint &&
                        evidence.timestampEpochMs - previous.second <= DEDUPE_WINDOW_MS
                    ) return@let
                    latestFingerprint[key] = fingerprint to evidence.timestampEpochMs
                    runCatching { persist(evidence.copy(contentFingerprint = fingerprint)) }
                }
        }
    }

    private companion object {
        const val DEDUPE_WINDOW_MS = 5_000L
    }
}
