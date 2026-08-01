# Enhanced Metadata Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the confirmed enhanced-metadata diagnostic design: resilient physical-cellular guardianship, layered traffic accounting, event-driven media and notification evidence, evidence-based behavior classification, compatible reports, tests, commits, and safe K60/K80 rollout.

**Architecture:** Keep strict scheduling alive independently from the VPN tunnel. Add pure Kotlin policies for network generations, bounded reconnects, traffic layers, evidence fingerprints, and classification; Android services adapt those policies to `ConnectivityManager`, `MediaSessionManager`, notification callbacks, Room, and the native forwarder. Persist raw observations before deriving CSV/HTML summaries so report statements remain auditable.

**Tech Stack:** Kotlin, Java, Android SDK 37.0, Room, Android `VpnService`, `ConnectivityManager`, `MediaSessionManager`, `NotificationListenerService`, C++17/JNI, JUnit 4, Robolectric, Gradle 9.x, NDK 27.3.13750724.

## Global Constraints

- Same APK must support Redmi K60 and K80; no model-specific fork.
- No Root, Frida, Xposed, HTTPS interception, packet payload retention, request body retention, account credential retention, or video binary retention.
- Connection capture remains user-opt-in, conflicts with other Android VPNs, is optional in standard mode, and is required in strict mode.
- Media and notification evidence requires notification-listener authorization and degrades cleanly when authorization is absent or revoked.
- Strict snapshots and probe schedules must continue when the physical network or VPN tunnel is unavailable.
- A local socket write must be labeled as locally accepted upstream bytes, never as remote-server-confirmed bytes.
- Database upgrades use explicit migrations and preserve existing records; old ZIP imports remain compatible.
- Documentation follows `category/YYYY-MM-DD/name.ext`; production changes follow test-first red-green-refactor cycles and independently verified commits.

---

### Task 1: Persistent schema for network lifecycle, traffic layers, gaps, and event fingerprints

**Files:**
- Create: `core/storage/src/main/java/com/redmiklab/storage/NetworkLifecycleEntity.java`
- Create: `core/storage/src/main/java/com/redmiklab/storage/TrafficAccountingEntity.java`
- Create: `core/storage/src/main/java/com/redmiklab/storage/CaptureGapEntity.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/AppEvidenceEntity.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/SnapshotEntity.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDao.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDatabase.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDatabaseFactory.java`
- Modify: `core/storage/build.gradle.kts`
- Test: `core/storage/src/androidTest/java/com/redmiklab/storage/DiagnosticMigrationTest.java`

**Interfaces:**
- Produces: `NetworkLifecycleEntity(runId, timestampEpochMs, oldState, newState, physicalNetworkId, vpnNetworkId, vpnUnderlyingNetworkId, reason, retryAttempt, generation)`.
- Produces: `TrafficAccountingEntity(runId, windowStartEpochMs, windowEndEpochMs, packageName, layer, direction, bytes, outcome, reason)`.
- Produces: `CaptureGapEntity(runId, startedAtEpochMs, endedAtEpochMs, reason, systemMobileBytes, details)`.
- Produces: `AppEvidenceEntity.source`, `AppEvidenceEntity.eventKey`, and `AppEvidenceEntity.contentFingerprint` with legacy defaults.
- Produces: snapshot device-state and guardian-state columns with legacy-safe defaults.

- [ ] **Step 1: Write a migration test that opens a version-13 database and verifies preserved rows plus version-14 tables and columns**

```java
@Test public void migrate13To14_preservesExistingEvidenceAndAddsLayeredTables() throws Exception {
    SupportSQLiteDatabase oldDb = helper.createDatabase(DB_NAME, 13);
    oldDb.execSQL("INSERT INTO app_evidence(runId,timestampEpochMs,packageName,displayName,evidenceType,payloadJson) VALUES('r',1,'p','P','MEDIA_SESSION','{}')");
    oldDb.close();

    SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(
            DB_NAME, 14, true, DiagnosticDatabaseFactory.MIGRATION_13_14);
    assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM app_evidence"));
    assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM traffic_accounting"));
    assertEquals("LEGACY", queryString(migrated, "SELECT source FROM app_evidence LIMIT 1"));
}
```

- [ ] **Step 2: Run the migration test and verify RED**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :core:storage:connectedDebugAndroidTest`

Expected: compilation failure because version 14 entities and `MIGRATION_13_14` do not exist.

- [ ] **Step 3: Add entities, DAO methods, version 14, and an explicit 13→14 migration**

```java
@Entity(tableName = "traffic_accounting")
public final class TrafficAccountingEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public final String runId;
    public final long windowStartEpochMs;
    public final long windowEndEpochMs;
    @Nullable public final String packageName;
    @NonNull public final String layer;
    @NonNull public final String direction;
    public final long bytes;
    @NonNull public final String outcome;
    @Nullable public final String reason;
}
```

Add DAO methods with these exact signatures:

```java
@Insert void insertNetworkLifecycle(NetworkLifecycleEntity value);
@Insert void insertTrafficAccounting(TrafficAccountingEntity value);
@Insert long insertCaptureGap(CaptureGapEntity value);
@Query("UPDATE capture_gaps SET endedAtEpochMs=:endedAt, systemMobileBytes=:systemBytes, details=:details WHERE id=:id")
int finishCaptureGap(long id, long endedAt, long systemBytes, String details);
@Query("SELECT * FROM network_lifecycle WHERE runId=:runId ORDER BY timestampEpochMs,id")
List<NetworkLifecycleEntity> networkLifecycleFor(String runId);
@Query("SELECT * FROM traffic_accounting WHERE runId=:runId ORDER BY windowStartEpochMs,id")
List<TrafficAccountingEntity> trafficAccountingFor(String runId);
@Query("SELECT * FROM capture_gaps WHERE runId=:runId ORDER BY startedAtEpochMs,id")
List<CaptureGapEntity> captureGapsFor(String runId);
```

Migration SQL must create all three tables, add evidence fingerprint/source columns, add snapshot state columns, and backfill legacy values without deleting data.

- [ ] **Step 4: Run storage tests and verify GREEN**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :core:storage:testDebugUnitTest :core:storage:connectedDebugAndroidTest`

Expected: all storage unit and migration tests pass. If no Android device is connected, retain the verified JVM tests now and run the instrumentation migration test during Task 8 before installation.

- [ ] **Step 5: Commit the schema change**

```bash
git add core/storage
git commit -m "feat: persist layered diagnostic evidence"
```

---

### Task 2: Pure network guardian policy and Android physical-cellular monitor

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/NetworkGuardianPolicy.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/PhysicalCellularNetworkMonitor.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/NetworkGuardian.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/UnderlyingCellularSessionStore.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ConnectionCaptureVpnService.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticScheduler.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticStartReceiver.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/NetworkGuardianPolicyTest.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/NetworkGuardianTest.kt`

**Interfaces:**
- Consumes: `PhysicalCellularNetworkSelector.select(...)` and persistent Run IDs.
- Produces: `NetworkGuardianPolicy.onEvent(state, event): GuardianDecision`.
- Produces: `NetworkGuardian.start(planId)`, `attachRun(runId)`, `stop(reason)`, and callbacks `onTunnelPause`, `onTunnelRebuild`, `onLifecycleEvent`, `onGapStart`, `onGapEnd`.
- Produces: monotonic `generation: Long`; callbacks from older generations are ignored.

- [ ] **Step 1: Write failing policy tests for five retries, passive waiting, stale callbacks, and recovery**

```kotlin
@Test fun fifthFailureStopsDenseRetriesButKeepsPassiveMonitoring() {
    val decision = (1..5).fold(GuardianState.healthy(7)) { state, attempt ->
        NetworkGuardianPolicy.onEvent(state, GuardianEvent.ReconnectFailed(attempt)).state
    }
    assertEquals(GuardianPhase.WAITING_FOR_NETWORK, decision.phase)
    assertTrue(decision.passiveMonitoring)
    assertNull(decision.nextRetryDelayMs)
}

@Test fun staleGenerationCannotRebindTunnel() {
    val decision = NetworkGuardianPolicy.onEvent(
        GuardianState.waiting(generation = 9),
        GuardianEvent.NetworkAvailable(generation = 8, networkId = "115"),
    )
    assertFalse(decision.actions.contains(GuardianAction.REBUILD_TUNNEL))
}
```

- [ ] **Step 2: Run policy tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*NetworkGuardianPolicyTest'`

Expected: compilation failure because guardian types do not exist.

- [ ] **Step 3: Implement the pure state machine and retry schedule**

```kotlin
object NetworkGuardianPolicy {
    val retryDelaysMs = listOf(0L, 30_000L, 60_000L, 120_000L, 300_000L)

    fun onEvent(state: GuardianState, event: GuardianEvent): GuardianDecision = when (event) {
        is GuardianEvent.NetworkLost -> GuardianDecision(
            state.nextGeneration(GuardianPhase.RECONNECTING),
            setOf(GuardianAction.PAUSE_TUNNEL, GuardianAction.OPEN_GAP, GuardianAction.RETRY),
        )
        is GuardianEvent.NetworkAvailable -> if (event.generation == state.generation) {
            GuardianDecision(state.healthy(event.networkId), setOf(GuardianAction.REBUILD_TUNNEL, GuardianAction.CLOSE_GAP))
        } else GuardianDecision(state, emptySet())
        is GuardianEvent.ReconnectFailed -> reconnectFailure(state, event.attempt)
        is GuardianEvent.Stop -> GuardianDecision(state.stopped(), setOf(GuardianAction.STOP_MONITORING))
    }
}
```

- [ ] **Step 4: Run policy tests and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests '*NetworkGuardianPolicyTest'`

Expected: all guardian policy tests pass.

- [ ] **Step 5: Write failing Android-adapter tests proving a lost network pauses only the tunnel and a new physical generation rebuilds it**

```kotlin
@Test fun networkLossDoesNotStopStrictCoordinator() {
    guardian.onLost("115")
    assertEquals(1, tunnel.pauseCalls)
    assertEquals(0, strictCoordinator.stopCalls)
    assertTrue(guardian.isMonitoring)
}
```

- [ ] **Step 6: Implement the Android monitor and integrate it without calling `stopSelf()` for recoverable network loss**

`PhysicalCellularNetworkMonitor` must use a cellular `NetworkRequest`, apply `PhysicalCellularNetworkSelector`, record candidate capabilities, and deliver generation-tagged callbacks. `ConnectionCaptureVpnService` must split lifecycle into:

```kotlin
private fun pauseTunnel(reason: String) { stopForwarderAndCloseTun(); recordGapStart(reason) }
private fun rebuildTunnel(network: Network) { establishTun(network); recordGapEnd(network) }
private fun stopServiceExplicitly() { guardian.stop("USER_STOP"); strictCoordinator.stopActive(); stopSelf() }
```

Scheduling a plan starts the guardian foreground service immediately; cancellation and terminal Run completion stop it. Attaching strict sampling must succeed even while the tunnel is paused.

- [ ] **Step 7: Run guardian and existing VPN lifecycle tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*NetworkGuardian*' --tests '*ConnectionCaptureVpnServiceTest' --tests '*ConnectionCaptureLifecyclePolicyTest'`

Expected: all selected tests pass.

- [ ] **Step 8: Commit the guardian change**

```bash
git add app/src/main app/src/test
git commit -m "feat: guard and rebuild physical cellular tunnels"
```

---

### Task 3: Heartbeats, no-network probe outcome, and richer snapshots

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/NetworkHeartbeatRunner.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/DeviceIdleSnapshotSource.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/NetworkGuardian.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticSnapshotWriter.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/NightProbeService.kt`
- Modify: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/NetworkSnapshotSource.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/NetworkHeartbeatRunnerTest.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/DiagnosticSnapshotWriterTest.kt`
- Create: `app/src/test/kotlin/com/redmiklab/app/NightProbeServiceTest.kt`

**Interfaces:**
- Consumes: guardian-selected physical `Network` and Task 1 DAO methods.
- Produces: `HeartbeatResult(startedAt, completedAt, endpoint, dnsBytes, requestBytes, responseBytes, failureStage)`.
- Produces: `DeviceRuntimeState(screenInteractive, deviceLocked, charging, lightIdle, deepIdle, batteryExempt, physicalNetworkId, vpnNetworkId, vpnUnderlyingNetworkId, guardianPhase, retryAttempt, captureComplete)`.
- Produces: probe failure `NO_PHYSICAL_CELLULAR_NETWORK` before URL retries.
- Uses the existing configurable `connectivityMinutes` interval for heartbeats; the existing default remains 10 minutes, so no new hard-coded cadence or UI field is introduced.

- [ ] **Step 1: Write failing heartbeat and snapshot-state tests**

```kotlin
@Test fun failedHeartbeatRetainsActuallyTransferredBytes() {
    val result = runner.run(fakeNetwork, fakeTransport(readBytes = 37, failure = IOException("reset")))
    assertEquals(37, result.responseBytes)
    assertEquals("RESPONSE_READ", result.failureStage)
}

@Test fun snapshotPersistsIdleChargingAndGuardianState() {
    writer.write("r", 1, 10, "STRICT_INTERNAL")
    assertTrue(store.snapshot.deepIdle)
    assertEquals("WAITING_FOR_NETWORK", store.snapshot.guardianState)
}
```

- [ ] **Step 2: Run selected tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*NetworkHeartbeatRunnerTest' --tests '*DiagnosticSnapshotWriterTest'`

Expected: compilation failure for missing heartbeat and device-state fields.

- [ ] **Step 3: Implement bounded heartbeats and snapshot runtime state**

```kotlin
data class HeartbeatResult(
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val endpoint: String,
    val dnsBytes: Long,
    val requestBytes: Long,
    val responseBytes: Long,
    val failureStage: String?,
)
```

Use the selected `Network` for DNS and socket creation, set connect/read timeouts, cap response reads, schedule from persisted `connectivityMinutes`, and persist heartbeat traffic with layer `HEARTBEAT`. Heartbeat failure emits a guardian event; success must not reset an unrelated Run.

- [ ] **Step 4: Add probe short-circuit before endpoint retries**

```kotlin
if (physicalNetwork == null) {
    persistProbeFailure(run, plannedAt, "NO_PHYSICAL_CELLULAR_NETWORK", attempts = emptyList())
    return
}
```

- [ ] **Step 5: Run selected tests and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests '*NetworkHeartbeatRunnerTest' --tests '*DiagnosticSnapshotWriterTest' --tests '*NightProbeServiceTest' --tests '*FailoverProbeRunnerTest'`

Expected: selected tests pass; no URL attempt is created for an absent physical network.

- [ ] **Step 6: Commit heartbeat and snapshot changes**

```bash
git add app feature/diagnostics core/storage
git commit -m "feat: record heartbeat and runtime network state"
```

---

### Task 4: Native forwarding outcomes and layered byte persistence

**Files:**
- Modify: `app/src/main/cpp/forwarder_engine.h`
- Modify: `app/src/main/cpp/forwarder_engine.cpp`
- Modify: `app/src/main/cpp/tcp_proxy_session.h`
- Modify: `app/src/main/cpp/tcp_proxy_session.cpp`
- Modify: `app/src/main/cpp/udp_proxy_session.h`
- Modify: `app/src/main/cpp/udp_proxy_session.cpp`
- Modify: `app/src/main/cpp/direct_forwarder.cpp`
- Modify: `app/src/main/cpp/CMakeLists.txt`
- Create: `scripts/test-native.sh`
- Modify: `app/src/main/kotlin/com/redmiklab/app/NativeConnectionObserver.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ConnectionFlowAccumulator.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ConnectionCaptureVpnService.kt`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/ConnectionFlowEntity.java`
- Test: `app/src/main/cpp/forwarder_engine_test.cpp`
- Test: `app/src/main/cpp/tcp_proxy_session_test.cpp`
- Test: `app/src/main/cpp/udp_proxy_session_test.cpp`
- Test: `app/src/test/kotlin/com/redmiklab/app/ConnectionFlowAccumulatorTest.kt`

**Interfaces:**
- Produces native `ForwardingObservation { FlowKey key; Direction direction; Stage stage; Outcome outcome; uint64_t bytes; std::string reason; std::string hostname; }`.
- Produces JNI callback `onForwardingObservation(protocol, sourceAddress, sourcePort, destinationAddress, destinationPort, direction, stage, outcome, bytes, reason, hostname)`.
- Persists layers `TUNNEL_OBSERVED`, `UPSTREAM_SOCKET_ACCEPTED`, `DOWNSTREAM_SOCKET_RECEIVED`, and `FORWARDING_FAILED`.

- [ ] **Step 1: Write failing native tests that distinguish TUN observation, accepted upstream bytes, received downstream bytes, and dropped bytes**

```cpp
TEST(ForwarderEngineTest, ReportsDistinctForwardingStages) {
    std::vector<ForwardingObservation> seen;
    ForwarderEngine engine(tcp_factory, udp_factory,
        [&](const ForwardingObservation& value) { seen.push_back(value); });
    engine.on_tun_packet(client_payload, 100);
    engine.poll(101);
    EXPECT_TRUE(has_stage(seen, ForwardingStage::TunObserved));
    EXPECT_TRUE(has_stage(seen, ForwardingStage::UpstreamSocketAccepted));
    EXPECT_TRUE(has_stage(seen, ForwardingStage::DownstreamSocketReceived));
}
```

- [ ] **Step 2: Add a host-native CTest target, run it, and verify RED**

`CMakeLists.txt` must keep the Android JNI library unchanged and, when `REDMIKLAB_BUILD_TESTS=ON`, build each existing `*_test.cpp` executable against a shared `forwarder_core` static library and register it with CTest. `scripts/test-native.sh` runs:

```bash
cmake -S app/src/main/cpp -B build/native-host -DREDMIKLAB_BUILD_TESTS=ON
cmake --build build/native-host --parallel
ctest --test-dir build/native-host --output-on-failure
```

Run: `scripts/test-native.sh`

Expected: C++ compilation failure because `ForwardingObservation` and stage-aware callbacks do not exist.

- [ ] **Step 3: Implement stage-aware callbacks at the actual socket read/write boundaries**

```cpp
enum class ForwardingStage { TunObserved, UpstreamSocketAccepted, DownstreamSocketReceived, Dropped };
enum class ForwardingDirection { Upstream, Downstream };
enum class ForwardingOutcome { Observed, Accepted, Received, Failed };
```

`TunObserved` is emitted after packet parsing. `UpstreamSocketAccepted` is emitted only for bytes returned as written by the protected external socket. `DownstreamSocketReceived` is emitted only for bytes read from the external socket. `Dropped` carries a stable reason such as `NO_SESSION`, `CONNECT_FAILED`, `WRITE_FAILED`, `READ_FAILED`, `TIMEOUT`, or `TUN_WRITE_FAILED`.

- [ ] **Step 4: Run native tests and verify GREEN**

Run: `scripts/test-native.sh && ./gradlew :app:externalNativeBuildDebug`

Expected: all host-native tests pass and the Android JNI library builds.

- [ ] **Step 5: Write failing Kotlin accumulator tests for layer/direction/outcome separation**

```kotlin
@Test fun sameFlowDifferentLayersRemainSeparate() {
    accumulator.add(observation(layer = "TUNNEL_OBSERVED", bytes = 100))
    accumulator.add(observation(layer = "UPSTREAM_SOCKET_ACCEPTED", bytes = 80))
    assertEquals(setOf("TUNNEL_OBSERVED", "UPSTREAM_SOCKET_ACCEPTED"), accumulator.drain().map { it.layer }.toSet())
}
```

- [ ] **Step 6: Update JNI, Kotlin accumulation, and Room persistence**

Extend the aggregation key with `direction`, `stage`, `outcome`, and `reason`; persist each aggregate both as auditable connection metadata and as a `TrafficAccountingEntity`. Owner attribution remains based on the original five-tuple and is never inferred from the hostname.

- [ ] **Step 7: Run native, accumulator, and VPN service tests**

Run: `scripts/test-native.sh && ./gradlew :app:externalNativeBuildDebug :app:testDebugUnitTest --tests '*ConnectionFlowAccumulatorTest' --tests '*ConnectionCaptureVpnServiceTest' --tests '*NativeForwarderContractTest'`

Expected: all selected native and JVM tests pass.

- [ ] **Step 8: Commit layered native accounting**

```bash
git add app/src/main/cpp app/src/main/kotlin app/src/test core/storage
git commit -m "feat: separate observed and forwarded traffic bytes"
```

---

### Task 5: Event-driven media sessions and real-time notification evidence

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/MediaEvidenceFingerprint.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/MediaSessionEventCollector.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/MediaNotificationEvidenceService.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/NotificationEvidenceDispatcher.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/AppEvidenceRetentionPolicy.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/MediaEvidenceFingerprintTest.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/MediaSessionEventCollectorTest.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/NotificationEvidenceDispatcherTest.kt`

**Interfaces:**
- Produces: `MediaSessionEventCollector.start(componentName)`, `refreshControllers()`, and `stop()`.
- Produces evidence sources `MEDIA_CALLBACK`, `NOTIFICATION_CALLBACK`, and `SNAPSHOT_FALLBACK`.
- Produces deterministic SHA-256 content fingerprints from package, event type, normalized public fields, and session/notification key.

- [ ] **Step 1: Write failing tests proving metadata changes between snapshots are retained and exact duplicates are suppressed**

```kotlin
@Test fun shortLivedMetadataChangeIsPersistedImmediately() {
    collector.start(component)
    controller.emitMetadata(title = "视频甲", artist = "作者甲")
    controller.emitMetadata(title = null, artist = null)
    assertEquals(listOf("视频甲", null), store.events.map { it.title })
    assertTrue(store.events.all { it.source == "MEDIA_CALLBACK" })
}

@Test fun identicalNotificationCallbackIsDeduplicatedButChangedTextIsNot() {
    dispatcher.dispatch(record(key = "n", text = "甲"))
    dispatcher.dispatch(record(key = "n", text = "甲"))
    dispatcher.dispatch(record(key = "n", text = "乙"))
    assertEquals(listOf("甲", "乙"), store.records.map(::notificationText))
}
```

- [ ] **Step 2: Run evidence tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*MediaEvidenceFingerprintTest' --tests '*MediaSessionEventCollectorTest' --tests '*NotificationEvidenceDispatcherTest'`

Expected: compilation failure because callback collector and fingerprints do not exist.

- [ ] **Step 3: Implement controller callbacks and active-session refresh**

```kotlin
class MediaSessionEventCollector(
    private val manager: MediaSessionManager,
    private val persist: (MediaEvidenceRecord) -> Unit,
) {
    fun start(listener: ComponentName) {
        manager.addOnActiveSessionsChangedListener(::replaceControllers, listener)
        replaceControllers(manager.getActiveSessions(listener))
    }
}
```

Register one `MediaController.Callback` per session and persist `onMetadataChanged`, `onPlaybackStateChanged`, `onQueueChanged`, and session-destroyed events immediately. Re-enumerating after listener reconnect records source `MEDIA_RECONNECTED` and does not mislabel the initial state as a user-visible change.

- [ ] **Step 4: Keep notification callbacks real-time and add content-aware deduplication**

`onNotificationPosted` and `onNotificationRemoved` continue to dispatch immediately. Deduplication key is `(runId, notificationKey, eventType, contentFingerprint)`; changing title, body, MediaStyle fields, messages, or removal reason creates a new record.

- [ ] **Step 5: Keep snapshot fallback with explicit source and missing-field reason**

`captureContext` continues to capture foreground and current media state every snapshot, but writes `source=SNAPSHOT_FALLBACK`. Empty metadata writes `metadataAvailability=TARGET_APP_DID_NOT_PROVIDE` when permission and session access are present, and `PERMISSION_UNAVAILABLE` when access is absent.

- [ ] **Step 6: Run evidence tests and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests '*MediaEvidence*' --tests '*NotificationEvidenceDispatcherTest' --tests '*AppEvidenceRetentionPolicyTest'`

Expected: callback, fallback, deduplication, permission-loss, and retention tests pass.

- [ ] **Step 7: Commit event-driven evidence capture**

```bash
git add app/src/main app/src/test core/storage
git commit -m "feat: capture media and notification changes in real time"
```

---

### Task 6: Endpoint taxonomy and evidence-based media/upload classification

**Files:**
- Modify: `core/model/src/main/kotlin/com/redmiklab/model/EndpointClassifier.kt`
- Modify: `core/model/src/main/kotlin/com/redmiklab/model/BehaviorEvidenceClassifier.kt`
- Create: `core/model/src/main/kotlin/com/redmiklab/model/TrafficReconciliation.kt`
- Modify: `core/model/src/test/kotlin/com/redmiklab/model/EndpointClassifierTest.kt`
- Modify: `core/model/src/test/kotlin/com/redmiklab/model/BehaviorEvidenceClassifierTest.kt`
- Create: `core/model/src/test/kotlin/com/redmiklab/model/TrafficReconciliationTest.kt`

**Interfaces:**
- Produces categories `VIDEO_CDN`, `AUDIO_CDN`, `IMAGE`, `API`, `ADVERTISING`, `ANALYTICS`, `LOG_UPLOAD`, `PUSH`, `LIVE`, `ECOMMERCE`, `OTHER`, `UNKNOWN`.
- Produces `BehaviorEvidence` with upstream/downstream bytes, forwarding outcome, foreground/lock state, media state, media timestamp distance, and endpoint rule version.
- Produces classifications `POSSIBLE_LOG_UPLOAD` and `UNEXPLAINED_BACKGROUND_UPLOAD` in addition to existing values.
- Produces `TrafficReconciliationResult(explainedBytes, unexplainedBytes, differenceRatio, warnings)`.

- [ ] **Step 1: Write failing classifier tests for audio/image/log/live/ecommerce, background upload, stale media, and preload counterexamples**

```kotlin
@Test fun largeBackgroundUploadToUnknownEndpointIsFlaggedWithoutClaimingItsContents() {
    val result = classifier.classify(evidence(
        appInForeground = false,
        screenLocked = true,
        upstreamBytes = 12_000_000,
        downstreamBytes = 20_000,
        endpointCategory = EndpointCategory.UNKNOWN,
        forwardingOutcome = "ACCEPTED",
    ))
    assertEquals(BehaviorClassification.UNEXPLAINED_BACKGROUND_UPLOAD, result.classification)
    assertTrue("UPLOAD_CONTENT_UNKNOWN" in result.reasons)
}

@Test fun currentPlaybackDownloadIsNotCalledPreloadWithoutExcessBurstEvidence() {
    val result = classifier.classify(evidence(mediaState = MediaPlaybackState.PLAYING, downstreamBytes = 1_000_000))
    assertNotEquals(BehaviorClassification.POSSIBLE_PRELOAD, result.classification)
}
```

- [ ] **Step 2: Run model tests and verify RED**

Run: `./gradlew :core:model:testDebugUnitTest`

Expected: failures for new categories, evidence fields, and classifications.

- [ ] **Step 3: Implement versioned endpoint rules and conservative behavior rules**

```kotlin
data class EndpointClassification(
    val category: EndpointCategory,
    val reason: String,
    val ruleVersion: Int = 2,
    val confidence: EvidenceConfidence,
)
```

All positive classifications must return evidence reasons and counterevidence. Preload remains `MEDIUM` or `LOW`; unknown background upload includes `UPLOAD_CONTENT_UNKNOWN`; no rule may say personal information was uploaded.

- [ ] **Step 4: Implement traffic reconciliation without assuming equal coverage**

```kotlin
fun reconcile(systemBytes: Long, tunnelBytes: Long, probeBytes: Long, heartbeatBytes: Long): TrafficReconciliationResult {
    val explained = tunnelBytes + probeBytes + heartbeatBytes
    val difference = systemBytes - explained
    return TrafficReconciliationResult(explained, difference, ratio(systemBytes, difference), warnings(systemBytes, difference))
}
```

- [ ] **Step 5: Run model tests and verify GREEN**

Run: `./gradlew :core:model:testDebugUnitTest`

Expected: all endpoint, behavior, and reconciliation tests pass.

- [ ] **Step 6: Commit classification changes**

```bash
git add core/model
git commit -m "feat: classify media endpoints and background uploads"
```

---

### Task 7: Layered CSV, JSON, and HTML report export with old-report compatibility

**Files:**
- Create: `feature/reports/src/main/kotlin/com/redmiklab/reports/LayeredTrafficReportBuilder.kt`
- Create: `feature/reports/src/main/kotlin/com/redmiklab/reports/MediaTimelineBuilder.kt`
- Modify: `feature/reports/src/main/kotlin/com/redmiklab/reports/RoomReportExporter.kt`
- Modify: `feature/reports/src/main/kotlin/com/redmiklab/reports/HtmlReportRenderer.kt`
- Modify: `feature/reports/src/main/kotlin/com/redmiklab/reports/ReportComparison.kt`
- Create: `feature/reports/src/test/kotlin/com/redmiklab/reports/RoomReportExporterTest.kt`
- Modify: `feature/reports/src/test/kotlin/com/redmiklab/reports/ReportComparisonTest.kt`
- Create: `feature/reports/src/test/kotlin/com/redmiklab/reports/LayeredTrafficReportBuilderTest.kt`

**Interfaces:**
- Consumes: all Task 1 DAO queries plus Task 6 classifiers.
- Produces the 13 CSV files named in the spec, `report.html`, and JSON schema version 3.
- Preserves import and comparison support for schema versions 1 and 2.

- [ ] **Step 1: Write a failing exporter test for all required entries and unambiguous headers**

```kotlin
@Test fun exportContainsEveryLayerAndEvidenceTimeline() {
    val entries = exportFixture().zipEntries()
    assertTrue(REQUIRED_ENTRIES.all(entries::containsKey))
    assertTrue(entries.getValue("forwarding_outcomes.csv").contains("stage,outcome,direction,bytes,reason"))
    assertTrue(entries.getValue("media_events.csv").contains("event_source,event_key,content_fingerprint"))
    assertTrue(entries.getValue("report.json").contains("\"schemaVersion\":3"))
}
```

`REQUIRED_ENTRIES` is exactly:

```kotlin
setOf(
    "traffic_summary.csv", "system_mobile_traffic.csv", "tunnel_observed_traffic.csv",
    "forwarding_outcomes.csv", "probe_traffic.csv", "heartbeat_traffic.csv",
    "capture_gaps.csv", "network_lifecycle.csv", "endpoint_observations.csv",
    "media_events.csv", "notification_events.csv", "behavior_classifications.csv",
    "unexplained_uploads.csv", "report.html", "report.json",
)
```

- [ ] **Step 2: Run report tests and verify RED**

Run: `./gradlew :feature:reports:testDebugUnitTest`

Expected: exporter test fails because layered entries and schema version 3 are absent.

- [ ] **Step 3: Implement focused builders and stream each CSV with escaped text**

```kotlin
data class LayeredTrafficSummary(
    val systemMobileBytes: Long,
    val tunnelObservedBytes: Long,
    val upstreamAcceptedBytes: Long,
    val downstreamReceivedBytes: Long,
    val failedBytes: Long,
    val probeBytes: Long,
    val heartbeatBytes: Long,
    val captureGapBytes: Long,
    val unexplainedDifferenceBytes: Long,
)
```

Media rows preserve original callback time and source. HTML labels each layer in Chinese and explains why values need not be equal. Empty metadata is rendered as “目标 App 未向 Android 提供” or “权限不可用”, never simply “无标题”.

- [ ] **Step 4: Add schema-version-3 JSON and version-1/2 comparison compatibility**

The JSON summary includes capture completeness, network loss/recovery count, each traffic layer total, behavior counts, and evidence limitations. Importing older reports defaults absent fields to `UNKNOWN` or zero and displays a legacy-data notice.

- [ ] **Step 5: Run report tests and verify GREEN**

Run: `./gradlew :feature:reports:testDebugUnitTest`

Expected: all report rendering, escaping, old-schema, and required-entry tests pass.

- [ ] **Step 6: Commit report changes**

```bash
git add feature/reports
git commit -m "feat: export layered network and media evidence"
```

---

### Task 8: Integration, regression verification, documentation, and safe device rollout

**Files:**
- Modify: `app/src/test/kotlin/com/redmiklab/app/ConnectionCaptureVpnServiceTest.kt`
- Modify: `feature/reports/src/test/kotlin/com/redmiklab/reports/RoomReportExporterTest.kt`
- Create: `docs/superpowers/progress/2026-07-20/enhanced-metadata-diagnostics.md`
- Modify: `AGENTS.md` only if implementation introduces a durable project rule not already recorded.

**Interfaces:**
- Consumes: Tasks 1–7.
- Produces: verified Debug APK and an evidence-based rollout record.

- [ ] **Step 1: Add an integration regression test for network loss while strict sampling stays active**

```kotlin
@Test fun strictRunSurvivesTunnelGapAndRebuildsOnNewGeneration() {
    service.attachStrict(runId = "r")
    service.guardianLost(networkId = "115")
    scheduler.advanceBy(5 * 60_000L)
    service.guardianAvailable(networkId = "119")
    assertEquals(1, snapshotSink.captureCount)
    assertEquals(listOf("115", "119"), lifecycle.physicalNetworkIds)
    assertEquals(1, tunnel.rebuildCalls)
    assertFalse(service.wasStopped)
}
```

- [ ] **Step 2: Run focused regression suites**

Run:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :core:model:testDebugUnitTest :core:storage:testDebugUnitTest \
  :app:testDebugUnitTest :feature:diagnostics:testDebugUnitTest \
  :feature:reports:testDebugUnitTest :app:externalNativeBuildDebug && scripts/test-native.sh
```

Expected: all focused JVM and native suites pass with zero failures.

- [ ] **Step 3: Run complete verification**

Run:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew test lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`; unit tests, lint, native build, and Debug APK assembly all complete successfully.

- [ ] **Step 4: Inspect the connected device without changing it**

Run:

```bash
adb devices -l
adb -s SERIAL shell getprop ro.product.model
adb -s SERIAL shell getprop ro.product.device
adb -s SERIAL shell getprop ro.build.version.release
adb -s SERIAL shell getprop ro.build.version.sdk
adb -s SERIAL exec-out run-as com.redmiklab.app cat databases/redmi_klab.db > /private/tmp/redmi_klab_preinstall.db
adb -s SERIAL exec-out run-as com.redmiklab.app cat databases/redmi_klab.db-wal > /private/tmp/redmi_klab_preinstall.db-wal
adb -s SERIAL exec-out run-as com.redmiklab.app cat databases/redmi_klab.db-shm > /private/tmp/redmi_klab_preinstall.db-shm
sqlite3 /private/tmp/redmi_klab_preinstall.db "SELECT status,COUNT(*) FROM diagnostic_runs WHERE status IN ('RUNNING','FINALIZING') GROUP BY status;"
```

Replace `SERIAL` with the single authorized device serial returned by `adb devices -l`. Proceed only when the model is the intended K60 or K80 and the query returns no active diagnostic rows.

- [ ] **Step 5: Install the retained-data APK only when the safety gate passes**

Run:

```bash
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL shell am start -W -n com.redmiklab.app/.MainActivity
adb -s SERIAL logcat -c
adb -s SERIAL shell pidof com.redmiklab.app
adb -s SERIAL logcat -d -b crash
```

Expected: installation reports `Success`, Activity launch completes, PID exists, and the crash buffer contains no new RedmiKLab crash.

- [ ] **Step 6: Verify migration and retained records on-device**

Copy the post-install database using the same `run-as` method and run:

```bash
sqlite3 /private/tmp/redmi_klab_postinstall.db "PRAGMA user_version; SELECT COUNT(*) FROM diagnostic_runs; SELECT COUNT(*) FROM snapshots; SELECT COUNT(*) FROM probes; SELECT COUNT(*) FROM network_lifecycle; SELECT COUNT(*) FROM traffic_accounting;"
```

Expected: `user_version` is 14; pre-existing run, snapshot, and probe counts are not lower than the pre-install counts; new tables are queryable.

- [ ] **Step 7: Record verified and deferred evidence**

Write `docs/superpowers/progress/2026-07-20/enhanced-metadata-diagnostics.md` with exact commands, exit status, test counts, connected model, schema version, retained row counts, install result, startup result, and crash-buffer result. Explicitly mark multi-hour deep-sleep timing and real Douyin title coverage as deferred until the next user-run night report; do not claim those two effects from unit tests.

- [ ] **Step 8: Commit verification documentation**

```bash
git add docs/superpowers/progress/2026-07-20/enhanced-metadata-diagnostics.md AGENTS.md
git commit -m "docs: verify enhanced metadata diagnostics rollout"
```

The final completion audit must map every requirement in `docs/superpowers/specs/2026-07-20/enhanced-metadata-diagnostics.md` to code, tests, build output, report fixtures, or device evidence. The goal remains active if any required item lacks direct evidence.
