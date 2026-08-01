package com.redmiklab.app

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import org.json.JSONArray
import org.json.JSONObject

class AndroidActiveMediaSessionSource(
    private val manager: MediaSessionManager,
    private val listenerComponent: ComponentName,
) : ActiveMediaSessionSource {
    private var consumer: ((List<ObservableMediaSession>) -> Unit)? = null
    private val platformListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        consumer?.invoke(controllers.orEmpty().map(::AndroidObservableMediaSession))
    }

    override fun currentSessions(): List<ObservableMediaSession> =
        manager.getActiveSessions(listenerComponent).map(::AndroidObservableMediaSession)

    override fun setListener(listener: ((List<ObservableMediaSession>) -> Unit)?) {
        if (consumer != null) runCatching { manager.removeOnActiveSessionsChangedListener(platformListener) }
        consumer = listener
        if (listener != null) manager.addOnActiveSessionsChangedListener(platformListener, listenerComponent)
    }
}

private class AndroidObservableMediaSession(
    private val controller: MediaController,
) : ObservableMediaSession {
    override val packageName: String = controller.packageName
    override val eventKey: String = "$packageName:${controller.sessionToken}"
    private var callback: MediaController.Callback? = null

    override fun currentPayload(): String = MediaSessionPayloadEncoder.encode(controller)

    override fun metadataAvailability(): String =
        if (controller.metadata?.keySet().isNullOrEmpty()) "TARGET_APP_DID_NOT_PROVIDE" else "PROVIDED"

    override fun setCallback(callback: ((String, String, String) -> Unit)?) {
        this.callback?.let(controller::unregisterCallback)
        this.callback = callback?.let { consumer ->
            object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    consumer("MEDIA_METADATA", currentPayload(), metadataAvailability())
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    consumer("MEDIA_PLAYBACK", currentPayload(), metadataAvailability())
                }

                override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
                    consumer("MEDIA_QUEUE", currentPayload(), metadataAvailability())
                }

                override fun onSessionDestroyed() {
                    consumer("MEDIA_SESSION_DESTROYED", "{}", "NOT_APPLICABLE")
                }
            }.also(controller::registerCallback)
        }
    }
}

object MediaSessionPayloadEncoder {
    fun encode(controller: MediaController): String {
        val metadata = controller.metadata
        val metadataJson = JSONObject()
        metadata?.keySet()?.sorted()?.forEach { key ->
            metadataJson.put(
                key,
                jsonValue(
                    runCatching { metadata.getText(key) }.getOrNull()
                        ?: runCatching { metadata.getLong(key) }.getOrNull(),
                ),
            )
        }
        val state = controller.playbackState
        return JSONObject()
            .put("playbackState", state?.state)
            .put("position", state?.position)
            .put("speed", state?.playbackSpeed)
            .put("actions", state?.actions)
            .put("metadata", metadataJson)
            .put(
                "queue",
                JSONArray().apply {
                    controller.queue.orEmpty().forEach { item ->
                        put(
                            JSONObject()
                                .put("queueId", item.queueId)
                                .put("title", item.description.title?.toString())
                                .put("subtitle", item.description.subtitle?.toString())
                                .put("description", item.description.description?.toString())
                                .put("mediaId", item.description.mediaId)
                                .put("mediaUri", item.description.mediaUri?.toString()),
                        )
                    }
                },
            )
            .toString()
    }

    private fun jsonValue(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is CharSequence -> value.toString()
        is Number, is Boolean, is String -> value
        else -> value.toString()
    }
}
