package com.redmiklab.app

import android.app.Notification
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.redmiklab.storage.AppEvidenceEntity
import com.redmiklab.storage.DiagnosticDatabaseFactory
import com.redmiklab.storage.DiagnosticEventEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MediaNotificationEvidenceService : NotificationListenerService() {
    private lateinit var persistenceExecutor: ExecutorService
    private lateinit var dispatcher: NotificationEvidenceDispatcher
    private var mediaCollector: MediaSessionEventCollector? = null

    override fun onCreate() {
        super.onCreate()
        persistenceExecutor = Executors.newSingleThreadExecutor()
        dispatcher = NotificationEvidenceDispatcher(persistenceExecutor, ::persistNotification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        dispatchNotification(sbn, "NOTIFICATION_POSTED", null)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        mediaCollector?.stop()
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        val collector = MediaSessionEventCollector(
            AndroidActiveMediaSessionSource(
                manager,
                ComponentName(this, MediaNotificationEvidenceService::class.java),
            ),
            persist = { evidence ->
                persistenceExecutor.execute { runCatching { persistMedia(evidence) } }
            },
        )
        if (runCatching(collector::start).isSuccess) mediaCollector = collector
        else collector.stop()
    }

    override fun onListenerDisconnected() {
        mediaCollector?.stop()
        mediaCollector = null
        super.onListenerDisconnected()
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int,
    ) {
        dispatchNotification(sbn, "NOTIFICATION_REMOVED", reason)
    }

    override fun onDestroy() {
        mediaCollector?.stop()
        mediaCollector = null
        if (::persistenceExecutor.isInitialized) persistenceExecutor.shutdown()
        super.onDestroy()
    }

    private fun dispatchNotification(
        sbn: StatusBarNotification,
        type: String,
        removalReason: Int?,
    ) {
        if (!::dispatcher.isInitialized) return
        dispatcher.dispatch { createEvidenceRecord(sbn, type, removalReason) }
    }

    private fun persistNotification(evidence: NotificationEvidenceRecord) {
        val dao = DiagnosticDatabaseFactory.get(this).diagnosticDao()
        val run = evidence.runId?.let(dao::runById) ?: dao.activeRun() ?: return
        dao.insertAppEvidence(
            AppEvidenceEntity(
                run.runId,
                evidence.timestampEpochMs,
                evidence.packageName,
                appLabel(this, evidence.packageName),
                evidence.evidenceType,
                evidence.payloadJson,
                "NOTIFICATION_CALLBACK",
                evidence.eventKey,
                evidence.contentFingerprint,
                evidence.metadataAvailability,
            ),
        )
    }

    private fun persistMedia(evidence: MediaEvidenceRecord) {
        val dao = DiagnosticDatabaseFactory.get(this).diagnosticDao()
        val run = dao.activeRun() ?: return
        dao.insertAppEvidence(
            AppEvidenceEntity(
                run.runId,
                evidence.timestampEpochMs,
                evidence.packageName,
                appLabel(this, evidence.packageName),
                evidence.evidenceType,
                evidence.payloadJson,
                evidence.source,
                evidence.eventKey,
                evidence.contentFingerprint,
                evidence.metadataAvailability,
            ),
        )
    }

    private fun createEvidenceRecord(
        sbn: StatusBarNotification,
        type: String,
        removalReason: Int?,
    ): NotificationEvidenceRecord {
        val notification = sbn.notification
        val payload = JSONObject()
            .put("key", sbn.key)
            .put("id", sbn.id)
            .put("tag", sbn.tag)
            .put("postTime", sbn.postTime)
            .put("category", notification.category)
            .put("channelId", notification.channelId)
            .put("group", notification.group)
            .put("sortKey", notification.sortKey)
            .put("flags", notification.flags)
            .put("ongoing", sbn.isOngoing)
            .put("clearable", sbn.isClearable)
            .put("removalReason", removalReason)
            .put("extras", bundleToJson(notification.extras))
            .put(
                "actions",
                JSONArray().apply {
                    notification.actions.orEmpty().forEach { action ->
                        put(
                            JSONObject()
                                .put("title", action.title?.toString())
                                .apply {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                        put("semanticAction", action.semanticAction)
                                    }
                                },
                        )
                    }
                },
            )
        return NotificationEvidenceRecord(
            packageName = sbn.packageName,
            evidenceType = type,
            payloadJson = payload.toString(),
            timestampEpochMs = System.currentTimeMillis(),
            eventKey = sbn.key,
            runId = DiagnosticDatabaseFactory.get(this).diagnosticDao().activeRun()?.runId,
        )
    }

    companion object {
        fun captureContext(context: Context, runId: String) {
            captureForeground(context, runId)
            captureMediaSessions(context, runId)
        }

        private fun captureForeground(context: Context, runId: String) {
            val usage = context.getSystemService(UsageStatsManager::class.java) ?: return
            val now = System.currentTimeMillis()
            val events = runCatching { usage.queryEvents(now - 60_000L, now) }.getOrNull() ?: return
            val event = UsageEvents.Event()
            var foreground: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    -> foreground = event.packageName
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    -> if (foreground == event.packageName) foreground = null
                }
            }
            foreground?.let { packageName ->
                DiagnosticDatabaseFactory.get(context).diagnosticDao().insertAppEvidence(
                    AppEvidenceEntity(
                        runId,
                        now,
                        packageName,
                        appLabel(context, packageName),
                        "FOREGROUND_CONTEXT",
                        JSONObject().put("foreground", true).toString(),
                        "SNAPSHOT_FALLBACK",
                        packageName,
                        MediaEvidenceFingerprint.sha256(
                            packageName,
                            "FOREGROUND_CONTEXT",
                            packageName,
                            "{\"foreground\":true}",
                        ),
                        "NOT_APPLICABLE",
                    ),
                )
            }
        }

        private fun captureMediaSessions(context: Context, runId: String) {
            val manager = context.getSystemService(MediaSessionManager::class.java) ?: return
            val listener = ComponentName(context, MediaNotificationEvidenceService::class.java)
            val dao = DiagnosticDatabaseFactory.get(context).diagnosticDao()
            val sessions = runCatching { manager.getActiveSessions(listener) }.getOrElse { error ->
                dao.insertEvent(
                    DiagnosticEventEntity(
                        runId,
                        System.currentTimeMillis(),
                        "MEDIA_SESSION_PERMISSION_UNAVAILABLE",
                        error.javaClass.simpleName,
                    ),
                )
                return
            }
            sessions.forEach { controller ->
                val payload = MediaSessionPayloadEncoder.encode(controller)
                val eventKey = "${controller.packageName}:${controller.sessionToken}"
                val availability = if (controller.metadata?.keySet().isNullOrEmpty()) {
                    "TARGET_APP_DID_NOT_PROVIDE"
                } else "PROVIDED"
                dao.insertAppEvidence(
                    AppEvidenceEntity(
                        runId,
                        System.currentTimeMillis(),
                        controller.packageName,
                        appLabel(context, controller.packageName),
                        "MEDIA_SESSION",
                        payload,
                        "SNAPSHOT_FALLBACK",
                        eventKey,
                        MediaEvidenceFingerprint.sha256(
                            controller.packageName,
                            "MEDIA_SESSION",
                            eventKey,
                            payload,
                        ),
                        availability,
                    ),
                )
            }
        }

        private fun bundleToJson(bundle: Bundle?): JSONObject = JSONObject().apply {
            bundle?.keySet()?.forEach { key -> put(key, jsonValue(runCatching { bundle.get(key) }.getOrNull())) }
        }

        private fun jsonValue(value: Any?): Any? = when (value) {
            null -> JSONObject.NULL
            is CharSequence -> value.toString()
            is Number, is Boolean, is String -> value
            is Array<*> -> JSONArray(value.map(::jsonValue))
            is IntArray -> JSONArray(value.toList())
            is LongArray -> JSONArray(value.toList())
            is BooleanArray -> JSONArray(value.toList())
            is Bundle -> bundleToJson(value)
            else -> value.toString()
        }

        private fun appLabel(context: Context, packageName: String): String? = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        }.getOrNull()
    }
}
