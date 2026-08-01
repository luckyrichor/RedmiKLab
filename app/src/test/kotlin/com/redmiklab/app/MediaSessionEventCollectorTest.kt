package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSessionEventCollectorTest {
    @Test
    fun short_lived_metadata_change_between_snapshots_is_persisted_immediately() {
        val session = FakeMediaSession("session-a", "com.video", "{\"title\":null}")
        val source = FakeMediaSessionSource(listOf(session))
        val records = mutableListOf<MediaEvidenceRecord>()
        val collector = MediaSessionEventCollector(source, records::add) { 100 }
        collector.start()
        records.clear()

        session.emit("MEDIA_METADATA", "{\"title\":\"视频甲\"}")
        session.emit("MEDIA_METADATA", "{\"title\":null}")

        assertEquals(listOf("{\"title\":\"视频甲\"}", "{\"title\":null}"), records.map { it.payloadJson })
        assertEquals(setOf("MEDIA_CALLBACK"), records.map { it.source }.toSet())
    }

    @Test
    fun exact_duplicate_callback_is_suppressed_but_real_content_change_is_not() {
        val session = FakeMediaSession("session-a", "com.video", "{}")
        val records = mutableListOf<MediaEvidenceRecord>()
        val collector = MediaSessionEventCollector(FakeMediaSessionSource(listOf(session)), records::add) { 100 }
        collector.start()
        records.clear()

        session.emit("MEDIA_PLAYBACK", "{\"state\":3}")
        session.emit("MEDIA_PLAYBACK", "{\"state\":3}")
        session.emit("MEDIA_PLAYBACK", "{\"state\":2}")

        assertEquals(2, records.size)
    }
}

private class FakeMediaSessionSource(
    private var sessions: List<ObservableMediaSession>,
) : ActiveMediaSessionSource {
    private var listener: ((List<ObservableMediaSession>) -> Unit)? = null
    override fun currentSessions(): List<ObservableMediaSession> = sessions
    override fun setListener(listener: ((List<ObservableMediaSession>) -> Unit)?) { this.listener = listener }
}

private class FakeMediaSession(
    override val eventKey: String,
    override val packageName: String,
    private var payload: String,
) : ObservableMediaSession {
    private var callback: ((String, String, String) -> Unit)? = null
    override fun currentPayload(): String = payload
    override fun metadataAvailability(): String = if ("null" in payload) "TARGET_APP_DID_NOT_PROVIDE" else "PROVIDED"
    override fun setCallback(callback: ((String, String, String) -> Unit)?) { this.callback = callback }
    fun emit(type: String, value: String) {
        payload = value
        callback?.invoke(type, value, metadataAvailability())
    }
}
