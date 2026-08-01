package com.redmiklab.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Build
import com.redmiklab.diagnostics.AndroidNetworkSnapshotSource
import com.redmiklab.diagnostics.AndroidTrafficStatsSource
import com.redmiklab.diagnostics.DiagnosticWindow
import com.redmiklab.diagnostics.NextActionPlanner
import com.redmiklab.model.DiagnosticRuntimeMode
import com.redmiklab.storage.AppTrafficEntity
import com.redmiklab.storage.DiagnosticDao
import com.redmiklab.storage.DiagnosticDatabaseFactory
import com.redmiklab.storage.DiagnosticEventEntity
import com.redmiklab.storage.DiagnosticRunEntity
import com.redmiklab.storage.SnapshotEntity
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Executes one persisted diagnostic action and exits. It deliberately does not own the whole
 * night window, so Android's rolling dataSync foreground-service quota is not consumed for hours.
 */
class NightDiagnosticService : Service() {
    private val commandExecutor = Executors.newSingleThreadExecutor()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(NOTIFICATION_ID, notification(intent?.action))
        val command = intent ?: Intent().setAction(ACTION_START)
        commandExecutor.execute {
            runCatching { execute(command) }.onFailure { error ->
                recoverableRun()?.let { run ->
                    event(run.runId, "ACTION_FAILED", "${command.action}: ${error.javaClass.simpleName}: ${error.message}")
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        commandExecutor.execute {
            recoverableRun()?.let { event(it.runId, "FOREGROUND_SERVICE_TIMEOUT", "type=$fgsType") }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        commandExecutor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun execute(intent: Intent) {
        when (intent.action ?: ACTION_START) {
            ACTION_START -> startRun(intent.getLongExtra(EXTRA_PLANNED_AT, 0L))
            ACTION_SNAPSHOT -> withMatchingRun(intent) { run, planned -> capture(run, planned, "PERIODIC") }
            ACTION_SAFE_CHECKPOINT -> withMatchingRun(intent) { run, planned ->
                capture(run, planned, "SAFE_CHECKPOINT")
                event(run.runId, "SAFE_CHECKPOINT_COMPLETED", "plannedAt=$planned")
            }
            ACTION_END -> withMatchingRun(intent, allowMissingRunId = true) { run, planned -> finish(run, planned) }
            ACTION_RECOVER -> recover(intent.getStringExtra(EXTRA_RUN_ID))
        }
    }

    private fun startRun(plannedStartExtra: Long) {
        val dao = dao()
        dao.recoverableRun()?.let {
            event(it.runId, "DUPLICATE_START_IGNORED", null)
            recover(it.runId)
            return
        }
        val preferences = DiagnosticPreferences(this)
        val config = preferences.load()
        val capture = connectionCaptureEnabled()
        val startDecision = DiagnosticRunStartPolicy().decide(
            config = config,
            connectionCaptureEnabled = capture,
            connectionCaptureRunning = connectionCaptureRunning(),
        )
        if (startDecision == DiagnosticRunStartDecision.REJECT) return

        val actualStart = Instant.now()
        val plannedStart = plannedStartExtra.takeIf { it > 0 } ?: actualStart.toEpochMilli()
        val configuredEnd = DiagnosticWindow.endAfter(
            Instant.ofEpochMilli(plannedStart),
            config.end.hour,
            config.end.minute,
        )
        val plannedEnd = if (config.runtimeMode == DiagnosticRuntimeMode.STANDARD) {
            minOf(configuredEnd, actualStart.plusSeconds(DiagnosticWindow.MAX_FOREGROUND_SERVICE_RUNTIME_SECONDS))
        } else {
            configuredEnd
        }
        val runId = UUID.randomUUID().toString()
        dao.startRunReplacingActive(
            DiagnosticRunEntity(
                runId,
                plannedStart,
                plannedEnd.toEpochMilli(),
                actualStart.toEpochMilli(),
                0,
                "RUNNING",
                config.runtimeMode.name,
                config.snapshotMinutes,
                config.connectivityMinutes,
                config.throughputMinutes,
                capture,
                null,
                actualStart.toEpochMilli(),
                preferences.savedExportFileName()
                    ?.replace(Regex("\\.(zip|json|html|pdf)$", RegexOption.IGNORE_CASE), "")
                    .orEmpty(),
                preferences.preferredReportFormat(),
                Build.MODEL,
            ),
        )
        event(runId, "RUN_STARTED", "mode=${config.runtimeMode.name};plannedStart=$plannedStart")
        if (startDecision == DiagnosticRunStartDecision.START_WITH_TUNNEL_GAP) {
            event(
                runId,
                "RUN_STARTED_WITH_TUNNEL_GAP",
                "connectionCaptureEnabled=true;connectionCaptureRunning=false",
            )
        }
        val run = requireNotNull(dao.runById(runId))
        attachGuardianRun(run.runId)
        capture(run, plannedStart, "START")
        scheduleBaseActions(run)
        if (config.runtimeMode == DiagnosticRuntimeMode.STRICT) attachStrictCoordinator(run)
    }

    private fun capture(run: DiagnosticRunEntity, plannedAt: Long, source: String) {
        if (dao().snapshotForSchedule(run.runId, plannedAt, source) != null) return
        val writer = DiagnosticSnapshotWriter(
            AndroidNetworkSnapshotSource(this),
            AndroidTrafficStatsSource(this),
            RoomSnapshotStore(dao()),
            DeviceIdleSnapshotSource(this),
        )
        val result = writer.write(run.runId, run.startedAtEpochMs, plannedAt, source)
        MediaNotificationEvidenceService.captureContext(this, run.runId)
        val actual = result?.actualAtEpochMs ?: System.currentTimeMillis()
        dao().touchRun(run.runId, actual)
        event(run.runId, "SNAPSHOT", "source=$source;plannedAt=$plannedAt;actualAt=$actual;delayMs=${(actual - plannedAt).coerceAtLeast(0)}")
        if (source == "PERIODIC" && run.runtimeMode == DiagnosticRuntimeMode.STANDARD.name) {
            NextActionPlanner().nextFutureSlot(
                plannedAt,
                run.snapshotMinutes,
                actual,
                run.plannedEndEpochMs,
            )?.let { DiagnosticAlarmScheduler(this).scheduleSnapshot(run.runId, Instant.ofEpochMilli(it)) }
        }
        armRecovery(run, actual)
    }


    private fun finish(run: DiagnosticRunEntity, plannedAt: Long) {
        val dao = dao()
        val current = dao.runById(run.runId) ?: return
        if (current.status == "RUNNING" && dao.claimRunForFinalization(run.runId, System.currentTimeMillis()) != 1) return
        if (current.status != "RUNNING" && current.status != "FINALIZING") return
        val effectivePlanned = plannedAt.takeIf { it > 0 } ?: run.plannedEndEpochMs
        capture(run, effectivePlanned, "FINAL_END")
        val endedAt = System.currentTimeMillis()
        pruneUnrelatedNotificationEvidence(run.runId)
        if (dao.completeFinalizingRun(run.runId, endedAt, "SCHEDULED_OR_USER_END") != 1) return
        event(run.runId, "RUN_COMPLETED", "plannedEnd=${run.plannedEndEpochMs};actualEnd=$endedAt")
        DiagnosticAlarmScheduler(this).cancelAll()
        detachStrictCoordinator(run.runId)
        detachGuardianRun(run.runId)
    }

    private fun recover(expectedRunId: String?) {
        val run = recoverableRun() ?: return
        if (expectedRunId != null && expectedRunId != run.runId) return
        val now = System.currentTimeMillis()
        event(run.runId, "RECOVERY_CHECK", "at=$now")
        if (run.status == "FINALIZING" || now >= run.plannedEndEpochMs) {
            finish(run, run.plannedEndEpochMs)
            return
        }
        scheduleBaseActions(run)
        if (run.runtimeMode == DiagnosticRuntimeMode.STRICT.name) attachStrictCoordinator(run)
    }

    private fun scheduleBaseActions(run: DiagnosticRunEntity) {
        val alarms = DiagnosticAlarmScheduler(this)
        val now = System.currentTimeMillis()
        if (run.runtimeMode == DiagnosticRuntimeMode.STANDARD.name) {
            nextSlot(run.startedAtEpochMs, run.snapshotMinutes, now, run.plannedEndEpochMs)
                ?.let { alarms.scheduleSnapshot(run.runId, Instant.ofEpochMilli(it)) }
        }
        nextSlot(run.startedAtEpochMs, run.throughputMinutes, now, run.plannedEndEpochMs)
            ?.let { alarms.scheduleProbe(run.runId, Instant.ofEpochMilli(it)) }
        val safe = run.plannedEndEpochMs - 120_000L
        if (safe > now) alarms.scheduleSafeCheckpoint(run.runId, Instant.ofEpochMilli(safe))
        alarms.scheduleEnd(run.runId, Instant.ofEpochMilli(run.plannedEndEpochMs))
        armRecovery(run, now)
    }

    private fun armRecovery(run: DiagnosticRunEntity, now: Long) {
        val at = (now + RECOVERY_INTERVAL_MS).coerceAtMost(run.plannedEndEpochMs)
        if (at > now) DiagnosticAlarmScheduler(this).scheduleRecovery(run.runId, Instant.ofEpochMilli(at))
    }

    private fun nextSlot(start: Long, minutes: Int, now: Long, end: Long): Long? {
        val interval = minutes * 60_000L
        var next = start + interval
        while (next <= now) next += interval
        return next.takeIf { it < end }
    }

    private fun attachStrictCoordinator(run: DiagnosticRunEntity) {
        startService(
            Intent(this, ConnectionCaptureVpnService::class.java)
                .setAction(ConnectionCaptureVpnService.ACTION_ATTACH_STRICT)
                .putExtra(EXTRA_RUN_ID, run.runId)
                .putExtra(EXTRA_STARTED_AT, run.startedAtEpochMs)
                .putExtra(EXTRA_PLANNED_END, run.plannedEndEpochMs)
                .putExtra(EXTRA_INTERVAL_MS, run.snapshotMinutes * 60_000L)
                .putExtra(EXTRA_PROBE_INTERVAL_MS, run.throughputMinutes * 60_000L),
        )
    }

    private fun detachStrictCoordinator(runId: String) {
        startService(
            Intent(this, ConnectionCaptureVpnService::class.java)
                .setAction(ConnectionCaptureVpnService.ACTION_DETACH_STRICT)
                .putExtra(EXTRA_RUN_ID, runId),
        )
    }

    private fun attachGuardianRun(runId: String) {
        startService(
            Intent(this, ConnectionCaptureVpnService::class.java)
                .setAction(ConnectionCaptureVpnService.ACTION_ATTACH_RUN)
                .putExtra(EXTRA_RUN_ID, runId),
        )
    }

    private fun detachGuardianRun(runId: String) {
        startService(
            Intent(this, ConnectionCaptureVpnService::class.java)
                .setAction(ConnectionCaptureVpnService.ACTION_DETACH_RUN)
                .putExtra(EXTRA_RUN_ID, runId),
        )
    }

    private fun pruneUnrelatedNotificationEvidence(runId: String) {
        val dao = dao()
        val evidence = dao.appEvidenceFor(runId)
        val networkParticipants = buildSet {
            dao.appTrafficFor(runId).mapTo(this) { it.packageName }
            dao.connectionFlowsFor(runId).mapNotNullTo(this) { it.ownerPackage }
        }
        val mediaParticipants = evidence.asSequence()
            .filter { it.evidenceType.startsWith("MEDIA_") }
            .map { it.packageName }
            .toSet()
        val policy = AppEvidenceRetentionPolicy()
        evidence.asSequence()
            .filterNot { policy.keep(it.evidenceType, it.packageName, networkParticipants, mediaParticipants) }
            .forEach { dao.deleteAppEvidence(it.id) }
    }

    private inline fun withMatchingRun(
        intent: Intent,
        allowMissingRunId: Boolean = false,
        block: (DiagnosticRunEntity, Long) -> Unit,
    ) {
        val run = activeRun() ?: return
        val expected = intent.getStringExtra(EXTRA_RUN_ID)
        if (!allowMissingRunId && expected != run.runId) return
        if (expected != null && expected != run.runId) return
        block(run, intent.getLongExtra(EXTRA_PLANNED_AT, System.currentTimeMillis()))
    }

    private fun connectionCaptureEnabled(): Boolean =
        getSharedPreferences(ConnectionCaptureVpnService.PREFERENCES, MODE_PRIVATE)
            .getBoolean(ConnectionCaptureVpnService.KEY_ENABLED, false)

    private fun connectionCaptureRunning(): Boolean =
        getSharedPreferences(ConnectionCaptureVpnService.PREFERENCES, MODE_PRIVATE)
            .getBoolean(ConnectionCaptureVpnService.KEY_RUNNING, false)

    private fun dao(): DiagnosticDao = DiagnosticDatabaseFactory.get(this).diagnosticDao()
    private fun activeRun(): DiagnosticRunEntity? = dao().activeRun()
    private fun recoverableRun(): DiagnosticRunEntity? = dao().recoverableRun()

    private fun event(runId: String, type: String, details: String?) {
        dao().insertEvent(DiagnosticEventEntity(runId, System.currentTimeMillis(), type, details))
    }

    private fun notification(action: String?): Notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle("RedmiKLab 正在执行诊断任务")
        .setContentText(action?.substringAfterLast('.') ?: "START")
        .setOngoing(false)
        .build()

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "夜间网络诊断任务", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val ACTION_START = "com.redmiklab.app.action.START"
        const val ACTION_SNAPSHOT = "com.redmiklab.app.action.SNAPSHOT"
        const val ACTION_PROBE = "com.redmiklab.app.action.PROBE"
        const val ACTION_SAFE_CHECKPOINT = "com.redmiklab.app.action.SAFE_CHECKPOINT"
        const val ACTION_END = "com.redmiklab.app.action.END"
        const val ACTION_RECOVER = "com.redmiklab.app.action.RECOVER"
        const val EXTRA_RUN_ID = "run_id"
        const val EXTRA_PLANNED_AT = "planned_at_epoch_ms"
        const val EXTRA_STARTED_AT = "started_at_epoch_ms"
        const val EXTRA_PLANNED_END = "planned_end_epoch_ms"
        const val EXTRA_INTERVAL_MS = "interval_ms"
        const val EXTRA_PROBE_INTERVAL_MS = "probe_interval_ms"
        const val EXTRA_TRIGGER_SOURCE = "trigger_source"
        private const val CHANNEL_ID = "night_diagnostic_tasks"
        private const val NOTIFICATION_ID = 1001
        private const val RECOVERY_INTERVAL_MS = 30 * 60_000L
    }
}

private class RoomSnapshotStore(
    private val dao: DiagnosticDao,
) : DiagnosticSnapshotStore {
    override fun lastSnapshotAt(runId: String): Long? = dao.lastSnapshotAt(runId)
    override fun insertSnapshot(snapshot: SnapshotEntity) = dao.insertSnapshot(snapshot)
    override fun insertAppTraffic(traffic: AppTrafficEntity) = dao.insertAppTraffic(traffic)
}
