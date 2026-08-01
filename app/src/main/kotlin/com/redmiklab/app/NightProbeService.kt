package com.redmiklab.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.redmiklab.diagnostics.BudgetTracker
import com.redmiklab.diagnostics.NextActionPlanner
import com.redmiklab.model.ProbeFailure
import com.redmiklab.model.ProbeResult
import com.redmiklab.storage.DiagnosticDao
import com.redmiklab.storage.DiagnosticDatabaseFactory
import com.redmiklab.storage.DiagnosticEventEntity
import com.redmiklab.storage.DiagnosticRunEntity
import com.redmiklab.storage.ProbeAttemptEntity
import com.redmiklab.storage.ProbeEntity
import com.redmiklab.storage.TrafficAccountingEntity
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Runs potentially slow active probes without delaying snapshots or run finalization. */
class NightProbeService : Service() {
    private val commandExecutor = Executors.newSingleThreadExecutor()
    private val latestStartId = AtomicInteger()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        latestStartId.set(startId)
        val command = intent ?: return stop(startId)
        commandExecutor.execute {
            runCatching { execute(command) }.onFailure { error ->
                activeRun()?.let { run ->
                    event(run.runId, "PROBE_ACTION_FAILED", "${error.javaClass.simpleName}: ${error.message}")
                }
            }
            if (startId == latestStartId.get()) stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        activeRun()?.let { event(it.runId, "PROBE_SERVICE_TIMEOUT", "type=$fgsType") }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        commandExecutor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun execute(intent: Intent) {
        if (intent.action != NightDiagnosticService.ACTION_PROBE) return
        val run = activeRun() ?: return
        if (intent.getStringExtra(NightDiagnosticService.EXTRA_RUN_ID) != run.runId) return
        probe(
            run = run,
            plannedAt = intent.getLongExtra(NightDiagnosticService.EXTRA_PLANNED_AT, System.currentTimeMillis()),
            triggerSource = intent.getStringExtra(NightDiagnosticService.EXTRA_TRIGGER_SOURCE) ?: "ALARM",
        )
    }

    private fun probe(run: DiagnosticRunEntity, plannedAt: Long, triggerSource: String) {
        val dao = dao()
        if (dao.probeForSchedule(run.runId, plannedAt) != null) {
            event(run.runId, "DUPLICATE_PROBE_IGNORED", "plannedAt=$plannedAt;source=$triggerSource")
            return
        }
        val dispatchedAt = System.currentTimeMillis()
        val timeline = ProbeRoundTimeline(plannedAt, dispatchedAt)
        val config = DiagnosticPreferences(this).load()
        val budget = BudgetTracker(config.nightlyBudgetBytes, dao.consumedProbeBytes(run.runId))
        val networkResolution = AndroidCellularNetworkResolver(
            getSystemService(android.net.ConnectivityManager::class.java),
            UnderlyingCellularSessionStore(this).read(),
        ).resolve(0)
        event(run.runId, "PROBE_NETWORK_PRECONDITION", networkResolution.diagnostic.toJson())
        val precondition = ProbeRoundPrecondition.evaluate(networkResolution.network != null)
        val result = if (!precondition.shouldAttemptEndpoints) {
            ProbeResult(
                Instant.now(), null, null, null, null, 0,
                ProbeFailure.NO_PHYSICAL_CELLULAR_NETWORK,
            )
        } else {
            FailoverProbeRunner(
                endpoints = listOf(config.probeEndpoint, config.fallbackProbeEndpoint),
                reserveAttempt = { budget.reserve(config.singleProbeLimitBytes) },
                retryDelayMs = config.probeRetryDelaySeconds * 1_000L,
                onAttempt = { endpoint, attemptNumber, attempt ->
                    val attemptAt = attempt.timestamp.toEpochMilli()
                    timeline.recordAttempt(attemptAt)
                    dao.insertProbeAttempt(
                        ProbeAttemptEntity(
                            run.runId, attemptAt, endpoint, attemptNumber,
                            attempt.dnsLatencyMs, attempt.httpsLatencyMs, attempt.consumedBytes,
                            attempt.failure?.name,
                        ),
                    )
                },
                runAttempt = { endpoint ->
                    MobileHttpProbeRunner(this, endpoint) { diagnostic ->
                        event(
                            run.runId,
                            "PROBE_NETWORK_DIAGNOSTIC",
                            "{\"endpoint\":\"${jsonEscape(endpoint)}\",\"resolution\":${diagnostic.toJson()}}",
                        )
                    }.run(config.singleProbeLimitBytes)
                },
            ).run()
        }
        val timing = timeline.complete(System.currentTimeMillis())
        dao.insertProbe(
            ProbeEntity(
                run.runId, timing.plannedAtEpochMs, timing.dispatchedAtEpochMs, timing.delayMs,
                triggerSource, timing.firstAttemptAtEpochMs, timing.lastAttemptAtEpochMs,
                timing.completedAtEpochMs, result.dnsLatencyMs, result.httpsLatencyMs,
                result.downloadMbps, result.uploadMbps, result.consumedBytes, result.failure?.name,
            ),
        )
        dao.insertTrafficAccounting(
            TrafficAccountingEntity(
                run.runId, timing.firstAttemptAtEpochMs ?: timing.dispatchedAtEpochMs,
                timing.completedAtEpochMs, null, "ACTIVE_PROBE", "DOWN",
                result.consumedBytes, if (result.failure == null) "READ" else "PARTIAL_OR_FAILED",
                result.failure?.name,
            ),
        )
        val current = dao.runById(run.runId)
        if (current?.status == "RUNNING") {
            dao.touchRun(run.runId, timing.completedAtEpochMs)
            NextActionPlanner().nextFutureSlot(
                plannedAt,
                run.throughputMinutes,
                timing.completedAtEpochMs,
                run.plannedEndEpochMs,
            )?.let { DiagnosticAlarmScheduler(this).scheduleProbe(run.runId, Instant.ofEpochMilli(it)) }
            armRecovery(run, timing.completedAtEpochMs)
        }
        event(
            run.runId,
            "PROBE",
            "plannedAt=$plannedAt;dispatchedAt=${timing.dispatchedAtEpochMs};" +
                "firstAttemptAt=${timing.firstAttemptAtEpochMs};lastAttemptAt=${timing.lastAttemptAtEpochMs};" +
                "completedAt=${timing.completedAtEpochMs};delayMs=${timing.delayMs};" +
                "source=$triggerSource;failure=${result.failure?.name ?: "NONE"}",
        )
    }

    private fun armRecovery(run: DiagnosticRunEntity, now: Long) {
        val at = (now + RECOVERY_INTERVAL_MS).coerceAtMost(run.plannedEndEpochMs)
        if (at > now) DiagnosticAlarmScheduler(this).scheduleRecovery(run.runId, Instant.ofEpochMilli(at))
    }

    private fun dao(): DiagnosticDao = DiagnosticDatabaseFactory.get(this).diagnosticDao()
    private fun activeRun(): DiagnosticRunEntity? = dao().activeRun()

    private fun event(runId: String, type: String, details: String?) {
        dao().insertEvent(DiagnosticEventEntity(runId, System.currentTimeMillis(), type, details))
    }

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun notification(): Notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle("RedmiKLab 正在执行主动测速")
        .setContentText("测速任务独立运行，不阻塞网络快照")
        .setOngoing(false)
        .build()

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "主动测速任务", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun stop(startId: Int): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private companion object {
        const val CHANNEL_ID = "night_probe_tasks"
        const val NOTIFICATION_ID = 1002
        const val RECOVERY_INTERVAL_MS = 30 * 60_000L
    }
}
