package com.redmiklab.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.redmiklab.model.ConnectionActivityClass
import com.redmiklab.model.ConnectionActivityClassifier
import com.redmiklab.diagnostics.AndroidTrafficStatsSource
import com.redmiklab.storage.ConnectionFlowEntity
import com.redmiklab.storage.CaptureGapEntity
import com.redmiklab.storage.DiagnosticDatabaseFactory
import com.redmiklab.storage.DiagnosticEventEntity
import com.redmiklab.storage.NetworkLifecycleEntity
import com.redmiklab.storage.TrafficAccountingEntity
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Owns the opt-in local VPN, native transparent forwarder, and safe teardown. */
class ConnectionCaptureVpnService : VpnService(), NativeSocketProtector, NativeConnectionObserver {
    internal var forwarder: ConnectionCaptureForwarder = NativeConnectionCaptureForwarder

    private var tunnel: ParcelFileDescriptor? = null
    private var nativeHandle: Long = 0
    private val flowAccumulator = ConnectionFlowAccumulator()
    private val persistenceExecutor = Executors.newSingleThreadScheduledExecutor()
    private val heartbeatExecutor = Executors.newSingleThreadExecutor()
    private val ownerCache = ConcurrentHashMap<FlowIdentity, FlowOwner>()
    @Volatile private var cachedContext = ConnectionContext(0, false, null)
    @Volatile private var destroying = false
    private var strictRunId: String? = null
    @Volatile private var guardianRunId: String? = null
    private var nextCaptureGapToken = 1L
    private var activeCaptureGap: PendingCaptureGap? = null
    private val captureGapIds = mutableMapOf<Long, Long>()
    private var demand = GuardianServiceDemand()
    private val guardianHandler = Handler(Looper.getMainLooper())
    private lateinit var physicalMonitor: PhysicalCellularNetworkMonitor
    private lateinit var networkGuardian: NetworkGuardian<Network>
    private var selectedPhysicalNetwork: Network? = null
    private var heartbeatRunnable: Runnable? = null
    private val heartbeatRunner = NetworkHeartbeatRunner(transport = AndroidHeartbeatTransport())
    private val strictTicker = HandlerStrictSamplingTicker(Handler(Looper.getMainLooper()))
    private val screenWakeRequester by lazy { AndroidScreenWakeRequester(this) }
    private val reconnectCoordinator by lazy {
        ReconnectAttemptCoordinator(
            scheduler = object : RetryScheduler {
                override fun schedule(delayMs: Long, action: () -> Unit) {
                    guardianHandler.postDelayed(action, delayMs)
                }
            },
            isCurrent = ::isCurrentReconnectAttempt,
            screenWake = screenWakeRequester::request,
            reconnect = { generation, _ -> physicalMonitor.requestNow(generation) },
            recorder = ::recordReconnectAction,
        )
    }
    private val strictCoordinator by lazy {
        StrictSamplingCoordinator(
            AndroidWakeLockHandle(this),
            strictTicker,
            object : StrictSnapshotSink {
                override fun capture(runId: String, plannedAtEpochMs: Long) {
                    dispatchStrictAction(NightDiagnosticService.ACTION_SNAPSHOT, runId, plannedAtEpochMs)
                }

                override fun probe(runId: String, plannedAtEpochMs: Long) {
                    dispatchStrictAction(NightDiagnosticService.ACTION_PROBE, runId, plannedAtEpochMs)
                }

                override fun safeCheckpoint(runId: String, plannedAtEpochMs: Long) {
                    dispatchStrictAction(NightDiagnosticService.ACTION_SAFE_CHECKPOINT, runId, plannedAtEpochMs)
                }

                override fun finish(runId: String, plannedAtEpochMs: Long) {
                    dispatchStrictAction(NightDiagnosticService.ACTION_END, runId, plannedAtEpochMs)
                }
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        serviceActiveInProcess = true
        createNotificationChannel()
        demand = GuardianServiceDemand(
            captureEnabled = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(KEY_ENABLED, false),
            nightScheduleEnabled = DiagnosticPreferences(this).nightScheduleEnabled(),
            strictRunAttached = false,
        )
        physicalMonitor = PhysicalCellularNetworkMonitor(
            this,
            object : PhysicalCellularNetworkListener {
                override fun currentGeneration(): Long = networkGuardian.state.generation

                override fun onPhysicalNetworkAvailable(generation: Long, network: Network) {
                    guardianHandler.post { acceptPhysicalNetwork(generation, network) }
                }

                override fun onPhysicalNetworkLost(network: Network) {
                    guardianHandler.post {
                        if (selectedPhysicalNetwork?.toString() == network.toString()) selectedPhysicalNetwork = null
                        networkGuardian.networkLost(network.toString())
                    }
                }

                override fun onPhysicalNetworkRequestUnavailable(generation: Long) {
                    guardianHandler.post { networkGuardian.reconnectFailed(generation) }
                }
            },
        )
        networkGuardian = NetworkGuardian(androidGuardianEffects())
        persistenceExecutor.scheduleWithFixedDelay(
            { flushConnectionFlows() },
            FLOW_FLUSH_SECONDS,
            FLOW_FLUSH_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                demand = GuardianServiceDemandPolicy.apply(demand, GuardianServiceCommand.STOP_CAPTURE)
                getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
                if (!demand.tunnelRequired) pauseTunnel("USER_STOPPED_CAPTURE")
                reconcileServiceDemand("USER_STOPPED_CAPTURE")
            }
            ACTION_START -> {
                demand = GuardianServiceDemandPolicy.apply(demand, GuardianServiceCommand.START_CAPTURE)
                getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply()
                ensureGuardianRunning()
                safePhysicalCellularNetwork()?.let(::rebuildTunnel)
            }
            ACTION_START_PLAN_GUARD -> {
                demand = GuardianServiceDemandPolicy.apply(demand, GuardianServiceCommand.START_PLAN_GUARD)
                ensureGuardianRunning()
            }
            ACTION_CANCEL_PLAN_GUARD -> {
                demand = GuardianServiceDemandPolicy.apply(demand, GuardianServiceCommand.CANCEL_PLAN_GUARD)
                reconcileServiceDemand("PLAN_CANCELLED")
            }
            ACTION_ATTACH_RUN -> {
                attachGuardianRun(intent.getStringExtra(NightDiagnosticService.EXTRA_RUN_ID))
                reconcileServiceDemand("RUN_ATTACHED")
            }
            ACTION_DETACH_RUN -> {
                detachGuardianRun(intent.getStringExtra(NightDiagnosticService.EXTRA_RUN_ID))
                reconcileServiceDemand("RUN_DETACHED")
            }
            ACTION_ATTACH_STRICT -> {
                demand = GuardianServiceDemandPolicy.apply(demand, GuardianServiceCommand.ATTACH_STRICT)
                attachGuardianRun(intent.getStringExtra(NightDiagnosticService.EXTRA_RUN_ID))
                ensureGuardianRunning()
                attachStrictSampling(intent)
            }
            ACTION_DETACH_STRICT -> {
                detachStrictSampling(intent.getStringExtra(NightDiagnosticService.EXTRA_RUN_ID))
                demand = GuardianServiceDemandPolicy.apply(demand, GuardianServiceCommand.DETACH_STRICT)
                reconcileServiceDemand("STRICT_RUN_DETACHED")
            }
            null -> reconcileServiceDemand("STICKY_RESTART")
        }
        updateNotification()
        return if (demand.keepServiceRunning) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        destroying = true
        serviceActiveInProcess = false
        strictRunId?.let(strictCoordinator::stop)
        strictRunId = null
        if (::networkGuardian.isInitialized) networkGuardian.stop("SERVICE_DESTROYED")
        guardianHandler.removeCallbacksAndMessages(null)
        closeTunnel()
        val finalFlows = flowAccumulator.drain()
        if (finalFlows.isNotEmpty()) persistenceExecutor.execute { persistConnectionFlows(finalFlows) }
        persistenceExecutor.shutdown()
        heartbeatExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun protectSocket(fileDescriptor: Int): Boolean =
        fileDescriptor >= 0 && protect(fileDescriptor)

    override fun onForwardingObservation(
        protocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
        direction: String,
        stage: String,
        outcome: String,
        wireBytes: Long,
        reason: String?,
        hostname: String?,
    ) {
        if (wireBytes < 0 || (wireBytes == 0L && outcome != "FAILED")) return
        val context = currentConnectionContext()
        val identity = FlowIdentity(protocol, sourceAddress, sourcePort, destinationAddress, destinationPort)
        val owner = ownerCache.computeIfAbsent(identity) {
            resolveOwner(identity, context.foregroundPackage)
        }
        val activityClass = ConnectionActivityClassifier.classify(
            screenLocked = context.screenLocked,
            foregroundPackage = context.foregroundPackage,
            ownerPackage = owner.packageName,
        ).reportValue()
        flowAccumulator.add(
            CapturedConnectionFlow(
                System.currentTimeMillis(),
                protocol,
                sourceAddress,
                sourcePort,
                destinationAddress,
                destinationPort,
                wireBytes,
                owner.packageName,
                owner.label,
                activityClass,
                hostname,
                direction,
                stage,
                outcome,
                reason,
            ),
        )
    }

    private fun rebuildTunnel(underlying: Network) {
        if (!demand.tunnelRequired) return
        if (nativeHandle != 0L && UnderlyingCellularSessionStore(this).read() == underlying.toString()) return
        closeTunnel()
        if (!NativeForwarderContract.isLoaded() || hasConflictingVpn()) return
        val builder = Builder()
            .setSession("RedmiKLab 连接级采集")
            .setMtu(1500)
            .setUnderlyingNetworks(arrayOf(underlying))
        ConnectionCaptureTunnelProfile.addresses.forEach { (address, prefix) ->
            builder.addAddress(address, prefix)
        }
        ConnectionCaptureTunnelProfile.routes.forEach { (address, prefix) ->
            builder.addRoute(address, prefix)
        }
        systemDnsServers(underlying).forEach { builder.addDnsServer(it) }
        val established = builder.establish()
        if (established == null) {
            markTunnelRunning(false)
            return
        }
        val handle = forwarder.start(established.fd, this, this)
        if (handle == 0L) {
            established.close()
            markTunnelRunning(false)
            return
        }
        tunnel = established
        nativeHandle = handle
        UnderlyingCellularSessionStore(this).record(underlying.toString())
        markTunnelRunning(true)
    }

    private fun pauseTunnel(reason: String) {
        closeTunnel()
        updateNotification()
    }

    private fun closeTunnel() {
        val handle = nativeHandle
        nativeHandle = 0
        if (handle != 0L) forwarder.stop(handle)
        tunnel?.close()
        tunnel = null
        UnderlyingCellularSessionStore(this).clear()
        ownerCache.clear()
        markTunnelRunning(false)
    }

    private fun attachStrictSampling(intent: Intent) {
        val runId = intent.getStringExtra(NightDiagnosticService.EXTRA_RUN_ID) ?: return
        strictRunId?.takeIf { it != runId }?.let(strictCoordinator::stop)
        strictRunId = runId
        strictCoordinator.start(
            runId = runId,
            startedAtEpochMs = intent.getLongExtra(NightDiagnosticService.EXTRA_STARTED_AT, 0L),
            nowEpochMs = System.currentTimeMillis(),
            intervalMs = intent.getLongExtra(NightDiagnosticService.EXTRA_INTERVAL_MS, 5 * 60_000L),
            probeIntervalMs = intent.getLongExtra(
                NightDiagnosticService.EXTRA_PROBE_INTERVAL_MS,
                30 * 60_000L,
            ),
            endAtEpochMs = intent.getLongExtra(NightDiagnosticService.EXTRA_PLANNED_END, Long.MAX_VALUE),
        )
    }

    private fun detachStrictSampling(runId: String?) {
        val active = strictRunId ?: return
        if (runId != null && runId != active) return
        strictCoordinator.stop(active)
        strictRunId = null
    }

    private fun attachGuardianRun(runId: String?) {
        if (runId == null) return
        guardianRunId = runId
        recordLifecycle(networkGuardian.state, networkGuardian.state, "RUN_ATTACHED")
        if (networkGuardian.state.phase != GuardianPhase.HEALTHY) openCaptureGap(networkGuardian.state.reason)
    }

    private fun detachGuardianRun(runId: String?) {
        if (runId != null && guardianRunId != runId) return
        closeCaptureGap("RUN_DETACHED")
        guardianRunId = null
    }

    private fun dispatchStrictAction(action: String, runId: String, plannedAtEpochMs: Long) {
        val serviceClass = when (DiagnosticServiceRouter.target(action)) {
            DiagnosticServiceTarget.PROBE -> NightProbeService::class.java
            DiagnosticServiceTarget.LIFECYCLE -> NightDiagnosticService::class.java
        }
        val intent = Intent(this, serviceClass)
            .setAction(action)
            .putExtra(NightDiagnosticService.EXTRA_RUN_ID, runId)
            .putExtra(NightDiagnosticService.EXTRA_PLANNED_AT, plannedAtEpochMs)
            .putExtra(NightDiagnosticService.EXTRA_TRIGGER_SOURCE, "STRICT_INTERNAL")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun androidGuardianEffects() = object : NetworkGuardianEffects<Network> {
        override fun startMonitoring(generation: Long) {
            physicalMonitor.startPassive()
        }

        override fun stopMonitoring() {
            physicalMonitor.stop()
            cancelHeartbeat()
        }

        override fun pauseTunnel(reason: String) {
            selectedPhysicalNetwork = null
            cancelHeartbeat()
            this@ConnectionCaptureVpnService.pauseTunnel(reason)
        }

        override fun rebuildTunnel(network: Network) {
            this@ConnectionCaptureVpnService.rebuildTunnel(network)
        }

        override fun openGap(reason: String) {
            openCaptureGap(reason)
        }

        override fun closeGap(networkId: String) {
            closeCaptureGap("NETWORK_RECOVERED:$networkId")
        }

        override fun scheduleRetry(delayMs: Long, generation: Long, attempt: Int) {
            reconnectCoordinator.schedule(delayMs, generation, attempt)
        }

        override fun recordTransition(previous: GuardianState, current: GuardianState) {
            recordLifecycle(previous, current, current.reason)
            if (previous.phase != GuardianPhase.WAITING_FOR_NETWORK &&
                current.phase == GuardianPhase.WAITING_FOR_NETWORK
            ) {
                recordReconnectAction(
                    ReconnectActionEvent(
                        ReconnectActionType.RETRIES_EXHAUSTED,
                        current.generation,
                        current.retryAttempt,
                        current.reason,
                    ),
                )
            }
            updateNotification()
        }
    }

    private fun isCurrentReconnectAttempt(generation: Long, attempt: Int): Boolean =
        !destroying &&
            networkGuardian.state.generation == generation &&
            networkGuardian.state.phase == GuardianPhase.RECONNECTING &&
            networkGuardian.state.retryAttempt == attempt

    private fun recordReconnectAction(event: ReconnectActionEvent) {
        val timestamp = System.currentTimeMillis()
        GuardianRuntimeStore(this).recordAction(event, timestamp)
        val runId = guardianRunId ?: return
        persistenceExecutor.execute {
            DiagnosticDatabaseFactory.get(this).diagnosticDao().insertEvent(
                DiagnosticEventEntity(
                    runId,
                    timestamp,
                    "NETWORK_${event.type.name}",
                    "generation=${event.generation};attempt=${event.attempt};${event.details}",
                ),
            )
        }
    }

    private fun acceptPhysicalNetwork(generation: Long, network: Network) {
        val networkId = network.toString()
        val current = networkGuardian.state
        if (generation != current.generation) return
        if (current.phase == GuardianPhase.HEALTHY && current.physicalNetworkId == networkId) {
            selectedPhysicalNetwork = network
            return
        }
        if (current.phase == GuardianPhase.HEALTHY && current.physicalNetworkId != networkId) {
            networkGuardian.networkLost(current.physicalNetworkId)
            val nextGeneration = networkGuardian.state.generation
            selectedPhysicalNetwork = network
            networkGuardian.networkAvailable(nextGeneration, networkId, network)
            scheduleHeartbeat(network, nextGeneration, 0)
            return
        }
        selectedPhysicalNetwork = network
        networkGuardian.networkAvailable(generation, networkId, network)
        scheduleHeartbeat(network, generation, 0)
    }

    private fun ensureGuardianRunning() {
        startForeground(NOTIFICATION_ID, buildNotification())
        networkGuardian.start()
        safePhysicalCellularNetwork()?.let { network ->
            acceptPhysicalNetwork(networkGuardian.state.generation, network)
        }
    }

    private fun reconcileServiceDemand(reason: String) {
        if (demand.guardianRequired) {
            ensureGuardianRunning()
            return
        }
        closeTunnel()
        networkGuardian.stop(reason)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun recordLifecycle(previous: GuardianState, current: GuardianState, reason: String) {
        GuardianRuntimeStore(this).record(
            current,
            UnderlyingCellularSessionStore(this).read(),
            demand.tunnelRequired && nativeHandle != 0L,
            recordLostNow = current.reason.startsWith("NETWORK_LOST"),
            recordRecoveredNow = previous.phase in setOf(
                GuardianPhase.RECONNECTING,
                GuardianPhase.WAITING_FOR_NETWORK,
            ) && current.phase == GuardianPhase.HEALTHY,
        )
        val runId = guardianRunId ?: return
        val row = NetworkLifecycleEntity(
            runId,
            System.currentTimeMillis(),
            previous.phase.name,
            current.phase.name,
            current.physicalNetworkId,
            currentVpnNetworkId(),
            UnderlyingCellularSessionStore(this).read(),
            reason,
            current.retryAttempt,
            current.generation,
        )
        persistenceExecutor.execute {
            DiagnosticDatabaseFactory.get(this).diagnosticDao().insertNetworkLifecycle(row)
        }
    }

    private fun openCaptureGap(reason: String) {
        if (activeCaptureGap != null) return
        val runId = guardianRunId ?: return
        val gap = PendingCaptureGap(nextCaptureGapToken++, runId, System.currentTimeMillis(), reason)
        activeCaptureGap = gap
        persistenceExecutor.execute {
            val id = DiagnosticDatabaseFactory.get(this).diagnosticDao().insertCaptureGap(
                CaptureGapEntity(gap.runId, gap.startedAtEpochMs, null, gap.reason, 0, null),
            )
            captureGapIds[gap.token] = id
        }
    }

    private fun closeCaptureGap(details: String) {
        val gap = activeCaptureGap ?: return
        activeCaptureGap = null
        val endedAtEpochMs = System.currentTimeMillis()
        persistenceExecutor.execute {
            val id = captureGapIds.remove(gap.token) ?: return@execute
            val traffic = runCatching {
                AndroidTrafficStatsSource(this).readWindow(
                    Instant.ofEpochMilli(gap.startedAtEpochMs),
                    Instant.ofEpochMilli(endedAtEpochMs),
                )
            }
            val finalDetails = traffic.exceptionOrNull()?.let {
                "$details;SYSTEM_MOBILE_UNAVAILABLE:${it.javaClass.simpleName}"
            } ?: details
            DiagnosticDatabaseFactory.get(this).diagnosticDao().finishCaptureGap(
                id,
                endedAtEpochMs,
                traffic.getOrNull()?.totalMobileBytes ?: 0,
                finalDetails,
            )
        }
    }

    private fun currentVpnNetworkId(): String? {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        return connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }?.toString()
    }

    private fun markTunnelRunning(running: Boolean) {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING, running)
            .apply()
        if (::networkGuardian.isInitialized) {
            GuardianRuntimeStore(this).record(
                networkGuardian.state,
                UnderlyingCellularSessionStore(this).read(),
                demand.tunnelRequired && running,
            )
        }
    }

    private fun scheduleHeartbeat(network: Network, generation: Long, delayMs: Long) {
        cancelHeartbeat()
        val runnable = Runnable {
            heartbeatRunnable = null
            if (destroying || networkGuardian.state.generation != generation ||
                networkGuardian.state.phase != GuardianPhase.HEALTHY ||
                selectedPhysicalNetwork?.toString() != network.toString()
            ) return@Runnable
            heartbeatExecutor.execute {
                val config = DiagnosticPreferences(this).load()
                val results = runHeartbeatSequence(
                    network,
                    listOf(config.probeEndpoint, config.fallbackProbeEndpoint).distinct(),
                )
                guardianHandler.post {
                    if (destroying || networkGuardian.state.generation != generation) return@post
                    if (HeartbeatDecisionPolicy.shouldInvalidateNetwork(results)) {
                        selectedPhysicalNetwork = null
                        networkGuardian.networkLost(network.toString())
                    } else {
                        scheduleHeartbeat(network, generation, heartbeatDelayMs())
                    }
                }
            }
        }
        heartbeatRunnable = runnable
        guardianHandler.postDelayed(runnable, delayMs.coerceAtLeast(0))
    }

    private fun cancelHeartbeat() {
        heartbeatRunnable?.let(guardianHandler::removeCallbacks)
        heartbeatRunnable = null
    }

    private fun heartbeatDelayMs(): Long =
        DiagnosticPreferences(this).load().connectivityMinutes.coerceAtLeast(1) * 60_000L

    private fun persistHeartbeat(result: HeartbeatResult) {
        val runId = guardianRunId ?: return
        val dao = DiagnosticDatabaseFactory.get(this).diagnosticDao()
        val outcome = if (result.failureStage == null) "COMPLETED" else "FAILED"
        val reason = listOfNotNull(
            "endpoint=${result.endpoint}",
            result.failureStage?.let { "stage=$it" },
            result.errorType?.let { "error=$it" },
        ).joinToString(";")
        if (result.dnsBytes > 0) dao.insertTrafficAccounting(
            TrafficAccountingEntity(
                runId, result.startedAtEpochMs, result.completedAtEpochMs, packageName,
                "HEARTBEAT", "BIDIRECTIONAL", result.dnsBytes, outcome, reason,
            ),
        )
        dao.insertTrafficAccounting(
            TrafficAccountingEntity(
                runId, result.startedAtEpochMs, result.completedAtEpochMs, packageName,
                "HEARTBEAT", "UP", result.requestBytes, outcome, reason,
            ),
        )
        dao.insertTrafficAccounting(
            TrafficAccountingEntity(
                runId, result.startedAtEpochMs, result.completedAtEpochMs, packageName,
                "HEARTBEAT", "DOWN", result.responseBytes, outcome, reason,
            ),
        )
    }

    private fun runHeartbeatSequence(network: Network, endpoints: List<String>): List<HeartbeatResult> {
        val result = mutableListOf<HeartbeatResult>()
        for (endpoint in endpoints) {
            val heartbeat = heartbeatRunner.run(network, endpoint).also(::persistHeartbeat)
            result += heartbeat
            if (heartbeat.failureStage == null) break
        }
        return result
    }

    private fun hasConflictingVpn(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return false
        return connectivity.allNetworks.any { network ->
            connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    @Suppress("DEPRECATION")
    private fun safePhysicalCellularNetwork(): android.net.Network? {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return null
        val candidates = connectivity.allNetworks.mapNotNull { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
            PhysicalCellularNetworkCandidate(
                networkId = network.toString(),
                value = network,
                facts = AndroidCellularCandidateFactsFactory.create(
                    capabilities,
                    isRecordedUnderlying = false,
                ),
            )
        }
        return PhysicalCellularNetworkSelector.select(
            candidates,
            activeDefaultIsValidatedVpn = false,
        ).selected?.value
    }

    private fun systemDnsServers(underlying: Network): List<String> {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val servers = connectivity?.getLinkProperties(underlying)?.dnsServers
            .orEmpty()
            .mapNotNull { it.hostAddress }
        return ConnectionCaptureTunnelProfile.dnsServers(servers)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, ConnectionCaptureVpnService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val state = if (::networkGuardian.isInitialized) networkGuardian.state else GuardianState.initial()
        val text = when (state.phase) {
            GuardianPhase.HEALTHY -> if (strictRunId != null) {
                "诊断进行中，物理移动网络 ${state.physicalNetworkId} 正常"
            } else "正在维持物理移动网络 ${state.physicalNetworkId}"
            GuardianPhase.RECONNECTING -> "物理移动网络不可用，正在尝试第 ${state.retryAttempt} 次重连"
            GuardianPhase.WAITING_FOR_NETWORK -> "主动重连已停止，正在等待系统恢复移动网络"
            GuardianPhase.WAITING_FOR_PLAN -> "正在准备网络守护"
            GuardianPhase.STOPPED -> "网络守护已停止"
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (strictRunId != null) "RedmiKLab 夜间诊断" else "RedmiKLab 网络守护")
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "停止采集", stopPendingIntent).build())
            .build()
    }

    private fun updateNotification() {
        if (destroying || !demand.keepServiceRunning) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "连接级采集", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun flushConnectionFlows() {
        val flows = flowAccumulator.drain()
        if (flows.isNotEmpty()) persistConnectionFlows(flows)
    }

    private fun persistConnectionFlows(flows: List<AggregatedConnectionFlow>) {
        val dao = DiagnosticDatabaseFactory.get(this).diagnosticDao()
        flows.forEach { flow ->
            val run = dao.runContaining(flow.timestampEpochMs) ?: return@forEach
            dao.insertConnectionFlow(
                ConnectionFlowEntity(
                    run.runId,
                    flow.timestampEpochMs,
                    if (flow.protocol == 6) "TCP" else "UDP",
                    flow.destinationAddress,
                    flow.destinationPort,
                    flow.wireBytes,
                    flow.hostname,
                    flow.ownerPackage,
                    flow.ownerLabel,
                    flow.activityClass,
                    flow.direction,
                    flow.stage,
                    flow.outcome,
                    flow.reason,
                ),
            )
            dao.insertTrafficAccounting(
                TrafficAccountingEntity(
                    run.runId,
                    flow.timestampEpochMs,
                    flow.timestampEpochMs,
                    flow.ownerPackage,
                    flow.stage,
                    flow.direction,
                    flow.wireBytes,
                    flow.outcome,
                    flow.reason ?: flow.hostname,
                ),
            )
        }
    }

    private fun resolveOwner(identity: FlowIdentity, foregroundPackage: String?): FlowOwner {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return FlowOwner(null, null)
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return FlowOwner(null, null)
        val uid = runCatching {
            connectivity.getConnectionOwnerUid(
                identity.protocol,
                InetSocketAddress(InetAddress.getByName(identity.sourceAddress), identity.sourcePort),
                InetSocketAddress(InetAddress.getByName(identity.destinationAddress), identity.destinationPort),
            )
        }.getOrDefault(-1)
        if (uid < 0) return FlowOwner(null, null)
        val packages = packageManager.getPackagesForUid(uid).orEmpty()
        val packageName = foregroundPackage?.takeIf(packages::contains) ?: packages.firstOrNull()
            ?: return FlowOwner(null, null)
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()
        return FlowOwner(packageName, label)
    }

    private fun currentConnectionContext(): ConnectionContext {
        val now = System.currentTimeMillis()
        val cached = cachedContext
        if (now - cached.sampledAtEpochMs < CONTEXT_CACHE_MILLIS) return cached
        val locked = getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true
        val foreground = foregroundPackage(now)
        return ConnectionContext(now, locked, foreground).also { cachedContext = it }
    }

    private fun foregroundPackage(now: Long): String? {
        val usage = getSystemService(UsageStatsManager::class.java) ?: return null
        val events = runCatching { usage.queryEvents(now - FOREGROUND_LOOKBACK_MILLIS, now) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        var foreground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> foreground = event.packageName
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (foreground == event.packageName) foreground = null
            }
        }
        return foreground
    }

    private fun ConnectionActivityClass.reportValue(): String = when (this) {
        ConnectionActivityClass.ForegroundActive -> "FOREGROUND_ACTIVE"
        ConnectionActivityClass.BackgroundScreenOn -> "BACKGROUND_SCREEN_ON"
        ConnectionActivityClass.BackgroundScreenLocked -> "BACKGROUND_SCREEN_LOCKED"
        ConnectionActivityClass.Unattributed -> "UNATTRIBUTED"
    }

    companion object {
        @Volatile private var serviceActiveInProcess: Boolean = false

        fun isActiveInProcess(): Boolean = serviceActiveInProcess
        const val ACTION_START = "com.redmiklab.app.action.START_CONNECTION_CAPTURE"
        const val ACTION_STOP = "com.redmiklab.app.action.STOP_CONNECTION_CAPTURE"
        const val ACTION_START_PLAN_GUARD = "com.redmiklab.app.action.START_PLAN_GUARD"
        const val ACTION_CANCEL_PLAN_GUARD = "com.redmiklab.app.action.CANCEL_PLAN_GUARD"
        const val ACTION_ATTACH_RUN = "com.redmiklab.app.action.ATTACH_GUARDIAN_RUN"
        const val ACTION_DETACH_RUN = "com.redmiklab.app.action.DETACH_GUARDIAN_RUN"
        const val ACTION_ATTACH_STRICT = "com.redmiklab.app.action.ATTACH_STRICT_SAMPLING"
        const val ACTION_DETACH_STRICT = "com.redmiklab.app.action.DETACH_STRICT_SAMPLING"
        const val PREFERENCES = "connection_capture"
        const val KEY_ENABLED = "enabled"
        const val KEY_RUNNING = "running"
        private const val CHANNEL_ID = "connection_capture"
        private const val NOTIFICATION_ID = 4202
        private const val FLOW_FLUSH_SECONDS = 5L
        private const val CONTEXT_CACHE_MILLIS = 1_000L
        private const val FOREGROUND_LOOKBACK_MILLIS = 60_000L
    }
}

private class HandlerStrictSamplingTicker(
    private val handler: Handler,
) : StrictSamplingTicker {
    private val runnables = mutableSetOf<Runnable>()

    override fun schedule(delayMs: Long, task: () -> Unit) {
        lateinit var runnable: Runnable
        runnable = Runnable {
            runnables.remove(runnable)
            task()
        }
        runnables += runnable
        handler.postAtTime(runnable, SystemClock.uptimeMillis() + delayMs.coerceAtLeast(0))
    }

    override fun cancel() {
        runnables.toList().forEach(handler::removeCallbacks)
        runnables.clear()
    }
}

private data class FlowIdentity(
    val protocol: Int,
    val sourceAddress: String,
    val sourcePort: Int,
    val destinationAddress: String,
    val destinationPort: Int,
)

private data class FlowOwner(val packageName: String?, val label: String?)

private data class ConnectionContext(
    val sampledAtEpochMs: Long,
    val screenLocked: Boolean,
    val foregroundPackage: String?,
)

private data class PendingCaptureGap(
    val token: Long,
    val runId: String,
    val startedAtEpochMs: Long,
    val reason: String,
)

internal interface ConnectionCaptureForwarder {
    fun start(
        tunnelFileDescriptor: Int,
        protector: NativeSocketProtector,
        observer: NativeConnectionObserver,
    ): Long
    fun stop(handle: Long)
}

internal object NativeConnectionCaptureForwarder : ConnectionCaptureForwarder {
    override fun start(
        tunnelFileDescriptor: Int,
        protector: NativeSocketProtector,
        observer: NativeConnectionObserver,
    ): Long = NativeForwarderContract.nativeStart(tunnelFileDescriptor, protector, observer)

    override fun stop(handle: Long) {
        NativeForwarderContract.nativeStop(handle)
    }
}
