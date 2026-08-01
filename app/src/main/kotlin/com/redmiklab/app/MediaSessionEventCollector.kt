package com.redmiklab.app

data class MediaEvidenceRecord(
    val packageName: String,
    val evidenceType: String,
    val payloadJson: String,
    val timestampEpochMs: Long,
    val source: String,
    val eventKey: String,
    val contentFingerprint: String,
    val metadataAvailability: String,
)

interface ObservableMediaSession {
    val eventKey: String
    val packageName: String
    fun currentPayload(): String
    fun metadataAvailability(): String
    fun setCallback(callback: ((eventType: String, payloadJson: String, metadataAvailability: String) -> Unit)?)
}

interface ActiveMediaSessionSource {
    fun currentSessions(): List<ObservableMediaSession>
    fun setListener(listener: ((List<ObservableMediaSession>) -> Unit)?)
}

class MediaSessionEventCollector(
    private val source: ActiveMediaSessionSource,
    private val persist: (MediaEvidenceRecord) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val sessions = linkedMapOf<String, ObservableMediaSession>()
    private val latestFingerprint = mutableMapOf<Pair<String, String>, Pair<String, Long>>()

    fun start() {
        source.setListener { replaceSessions(it, "MEDIA_CALLBACK") }
        replaceSessions(source.currentSessions(), "MEDIA_RECONNECTED")
    }

    fun refreshControllers() = replaceSessions(source.currentSessions(), "MEDIA_RECONNECTED")

    fun stop() {
        source.setListener(null)
        sessions.values.forEach { it.setCallback(null) }
        sessions.clear()
    }

    private fun replaceSessions(values: List<ObservableMediaSession>, sourceName: String) {
        val incoming = values.associateBy { it.eventKey }
        (sessions.keys - incoming.keys).forEach { key ->
            sessions.remove(key)?.let { session ->
                session.setCallback(null)
                emit(session, "MEDIA_SESSION_DESTROYED", "{}", "NOT_APPLICABLE", "MEDIA_CALLBACK")
            }
        }
        incoming.forEach { (key, session) ->
            if (sessions[key] === session) return@forEach
            sessions.remove(key)?.setCallback(null)
            sessions[key] = session
            session.setCallback { eventType, payload, availability ->
                emit(session, eventType, payload, availability, "MEDIA_CALLBACK")
            }
            emit(session, "MEDIA_SESSION_STATE", session.currentPayload(), session.metadataAvailability(), sourceName)
        }
    }

    private fun emit(
        session: ObservableMediaSession,
        eventType: String,
        payload: String,
        availability: String,
        sourceName: String,
    ) {
        val fingerprint = MediaEvidenceFingerprint.sha256(
            session.packageName,
            eventType,
            session.eventKey,
            payload,
        )
        val dedupeKey = session.eventKey to eventType
        val now = clock()
        val previous = latestFingerprint[dedupeKey]
        if (previous?.first == fingerprint && now - previous.second <= DEDUPE_WINDOW_MS) return
        latestFingerprint[dedupeKey] = fingerprint to now
        persist(
            MediaEvidenceRecord(
                session.packageName,
                eventType,
                payload,
                now,
                sourceName,
                session.eventKey,
                fingerprint,
                availability,
            ),
        )
    }

    private companion object {
        const val DEDUPE_WINDOW_MS = 5_000L
    }
}
