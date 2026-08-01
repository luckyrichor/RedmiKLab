# Night Diagnostic Runtime Modes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the all-night `dataSync` service with persisted short actions and add locked-per-run standard/strict modes with measured sampling delay.

**Architecture:** A persisted Run is the source of truth. `NightDiagnosticService` executes one idempotent action and stops; standard mode uses scheduled one-shot actions, while strict mode delegates periodic sampling and the wake lock to the already-authorized VPN service. Settings remain mutable until the Run begins, then the Run stores an immutable configuration snapshot.

**Tech Stack:** Kotlin, Android Service/AlarmManager/VpnService/PowerManager, Room Java entities, JUnit, Robolectric.

## Global Constraints

- Keep the user-selected window at or below six hours and retain the 120-second safe checkpoint.
- Standard and strict modes must not hold a `dataSync` foreground service for the entire window.
- Strict mode requires enabled connection capture and must never silently downgrade before start.
- Mode and interval changes before start affect the next scheduled Run; changes after start affect only the next Run.
- Every snapshot stores planned time, actual time, delay and trigger source.
- All production behavior is implemented test-first.

---

### Task 1: Runtime mode configuration and lock policy

**Files:**
- Modify: `core/model/src/main/kotlin/com/redmiklab/model/DiagnosticConfig.kt`
- Create: `core/model/src/main/kotlin/com/redmiklab/model/DiagnosticRuntimeMode.kt`
- Create: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/RunConfigurationLock.kt`
- Test: `core/model/src/test/kotlin/com/redmiklab/model/DiagnosticConfigTest.kt`
- Test: `feature/diagnostics/src/test/kotlin/com/redmiklab/diagnostics/RunConfigurationLockTest.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticPreferences.kt`

**Interfaces:**
- Produces: `enum class DiagnosticRuntimeMode { STANDARD, STRICT }`
- Produces: `data class LockedRunConfig(val mode, val snapshotMinutes, val connectivityMinutes, val throughputMinutes, val connectionCaptureEnabled)`
- Produces: `RunConfigurationLock.lock(config, connectionCaptureEnabled): LockedRunConfig`

- [ ] **Step 1: Write failing configuration tests**

```kotlin
@Test fun strictModeRequiresConnectionCapture() {
    val issues = DiagnosticConfig.default().copy(runtimeMode = DiagnosticRuntimeMode.STRICT)
        .validateRuntime(connectionCaptureEnabled = false)
    assertEquals(listOf(ConfigIssue.StrictModeRequiresConnectionCapture), issues)
}

@Test fun lockedRunConfigurationDoesNotChangeWithLaterPreferences() {
    val locked = RunConfigurationLock.lock(
        DiagnosticConfig.default().copy(runtimeMode = DiagnosticRuntimeMode.STANDARD),
        connectionCaptureEnabled = false,
    )
    assertEquals(DiagnosticRuntimeMode.STANDARD, locked.mode)
}
```

- [ ] **Step 2: Run tests and verify the missing mode APIs fail**

Run: `./gradlew :core:model:testDebugUnitTest :feature:diagnostics:testDebugUnitTest`

Expected: compilation failure for `DiagnosticRuntimeMode`, `runtimeMode` and `validateRuntime`.

- [ ] **Step 3: Implement the mode, validation and preferences**

```kotlin
enum class DiagnosticRuntimeMode { STANDARD, STRICT }

fun DiagnosticConfig.validateRuntime(connectionCaptureEnabled: Boolean): List<ConfigIssue> =
    if (runtimeMode == DiagnosticRuntimeMode.STRICT && !connectionCaptureEnabled) {
        listOf(ConfigIssue.StrictModeRequiresConnectionCapture)
    } else emptyList()
```

Persist `runtime_mode`, `snapshot_minutes`, `connectivity_minutes` and `throughput_minutes`; default existing installs to `STANDARD`.

- [ ] **Step 4: Run configuration tests and commit**

Run: `./gradlew :core:model:testDebugUnitTest :feature:diagnostics:testDebugUnitTest :app:testDebugUnitTest`

Expected: all selected tests pass.

Commit: `feat: add locked diagnostic runtime modes`

### Task 2: Persist immutable Run configuration and sampling timing

**Files:**
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticRunEntity.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/SnapshotEntity.java`
- Create: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticEventEntity.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDatabase.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDatabaseFactory.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDao.java`
- Test: `core/storage/src/test/kotlin/com/redmiklab/storage/DiagnosticDaoTest.kt`

**Interfaces:**
- Produces: Room schema version 9.
- Produces: `DiagnosticDao.snapshotForSchedule(runId, plannedAtEpochMs, triggerSource)`.
- Produces: `DiagnosticDao.insertEvent(DiagnosticEventEntity)`.

- [ ] **Step 1: Write failing DAO tests**

```kotlin
@Test fun runPersistsLockedModeAndIntervals() {
    dao.insertRun(run("STRICT", snapshotMinutes = 5))
    assertEquals("STRICT", dao.runById(RUN_ID)!!.runtimeMode)
}

@Test fun duplicatePlannedSnapshotIsRejectedByLookup() {
    dao.insertSnapshot(snapshot(plannedAt = 1000, actualAt = 1100, source = "PERIODIC"))
    assertNotNull(dao.snapshotForSchedule(RUN_ID, 1000, "PERIODIC"))
}
```

- [ ] **Step 2: Run the DAO test and verify RED**

Run: `./gradlew :core:storage:testDebugUnitTest --tests com.redmiklab.storage.DiagnosticDaoTest`

Expected: compilation failure for the new entity fields and DAO methods.

- [ ] **Step 3: Add schema fields and migration 8 to 9**

Add immutable Run fields for planned/actual times, mode, intervals, capture flag, terminal reason and last action time. Add snapshot fields `plannedAtEpochMs`, `delayMs` and `triggerSource`. Create `diagnostic_events`.

Migration SQL must preserve all version-8 rows with `STANDARD`, infer planned time from actual time, and use `LEGACY` as trigger source.

- [ ] **Step 4: Run storage tests and commit**

Run: `./gradlew :core:storage:testDebugUnitTest`

Expected: all storage tests pass, including migration-backed DAO tests.

Commit: `feat: persist runtime configuration and sampling delay`

### Task 3: Extract an idempotent diagnostic action runner

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/DiagnosticAction.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/DiagnosticActionRunner.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/DiagnosticSnapshotWriter.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/NightDiagnosticService.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/DiagnosticActionRunnerTest.kt`

**Interfaces:**
- Produces: `sealed interface DiagnosticAction { Start; Snapshot; Probe; SafeCheckpoint; End; Recover }`
- Produces: `DiagnosticActionRunner.run(action, plannedAtEpochMs): ActionResult`
- Consumes: Run and snapshot DAO methods from Task 2.

- [ ] **Step 1: Write failing action idempotency tests**

```kotlin
@Test fun repeatedSnapshotActionDoesNotDuplicatePlannedSnapshot() {
    runner.run(DiagnosticAction.Snapshot, 1_000)
    runner.run(DiagnosticAction.Snapshot, 1_000)
    assertEquals(1, fakeStore.snapshots.size)
}

@Test fun endWithoutInMemoryServiceCompletesPersistedRun() {
    runner.run(DiagnosticAction.End, 6_000)
    assertEquals("COMPLETED", fakeStore.run.status)
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.DiagnosticActionRunnerTest`

Expected: compilation failure because the runner does not exist.

- [ ] **Step 3: Move snapshot/probe/finalization behavior behind the runner**

The runner reads the active Run for every invocation, checks the planned-action uniqueness key, writes the action result, and returns only after persistence completes.

`NightDiagnosticService` parses one action, calls the runner on a worker executor, then calls `stopSelf(startId)`.

- [ ] **Step 4: Verify action tests and existing service tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: action idempotency, legacy service gates and probe tests pass.

Commit: `refactor: execute diagnostics as idempotent short actions`

### Task 4: Standard mode one-shot scheduling and recovery

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticAlarmScheduler.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticAlarmReceiver.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticScheduler.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticStartReceiver.kt`
- Create: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/NextActionPlanner.kt`
- Test: `feature/diagnostics/src/test/kotlin/com/redmiklab/diagnostics/NextActionPlannerTest.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/DiagnosticAlarmReceiverTest.kt`

**Interfaces:**
- Produces: `NextActionPlanner.afterCompleted(action, actualAt, lockedConfig, endAt): List<PlannedAction>`
- Produces: action-specific immutable PendingIntents containing Run ID and planned time.

- [ ] **Step 1: Write failing planning tests**

```kotlin
@Test fun standardStartPlansSnapshotProbeCheckpointAndEnd() {
    val actions = planner.afterCompleted(Start, start, config, end)
    assertTrue(actions.any { it.action == Snapshot && it.at == start.plusSeconds(300) })
    assertTrue(actions.any { it.action == SafeCheckpoint && it.at == end.minusSeconds(120) })
    assertTrue(actions.any { it.action == End && it.at == end })
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :feature:diagnostics:testDebugUnitTest :app:testDebugUnitTest`

Expected: missing planner and action-aware alarm APIs.

- [ ] **Step 3: Implement persisted one-shot scheduling**

Every completed standard-mode action schedules only its next occurrence. A late action keeps its original planned timestamp for delay reporting and computes the next future slot without emitting fabricated missed snapshots.

Boot and recovery receivers call `Recover`, which reconciles active Run state and rearms the next valid actions.

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew :feature:diagnostics:testDebugUnitTest :app:testDebugUnitTest`

Expected: all selected tests pass.

Commit: `feat: schedule standard diagnostics as one-shot actions`

### Task 5: Strict mode coordinator in the VPN service

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/StrictSamplingCoordinator.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ConnectionCaptureVpnService.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/AndroidWakeLockHandle.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/StrictSamplingCoordinatorTest.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/ConnectionCaptureVpnServiceTest.kt`

**Interfaces:**
- Produces: `StrictSamplingCoordinator.start(runId, startedAt, interval, endAt)`.
- Produces: `StrictSamplingCoordinator.stop(runId)`.
- Consumes: `DiagnosticActionRunner` snapshot and safe-checkpoint actions.

- [ ] **Step 1: Write failing strict coordinator tests**

```kotlin
@Test fun strictCoordinatorAcquiresWakeLockAndEmitsScheduledSlots() {
    coordinator.start(RUN_ID, instant(0), Duration.ofMinutes(5), instant(1800))
    clock.advance(Duration.ofMinutes(5))
    scheduler.runDueTasks()
    assertEquals(listOf(300_000L), actions.plannedTimes)
    assertTrue(wakeLock.isHeld)
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.StrictSamplingCoordinatorTest`

Expected: missing coordinator.

- [ ] **Step 3: Implement strict internal scheduling**

Use monotonic elapsed time for waiting and wall-clock planned timestamps for persistence. Acquire the wake lock only for an active strict Run. On process restart, reconstruct the next future slot from persisted Run data.

If VPN permission or forwarding is lost, write `STRICT_MODE_DEGRADED`, release the wake lock and schedule standard recovery actions.

- [ ] **Step 4: Run app tests and commit**

Run: `./gradlew :app:testDebugUnitTest`

Expected: strict scheduling, VPN lifecycle and wake-lock tests pass.

Commit: `feat: run strict sampling inside connection capture`

### Task 6: Timeout-safe short service and UI semantics

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/NightDiagnosticService.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/MainActivity.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/RuntimeModeUiPolicy.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/RuntimeModeUiPolicyTest.kt`
- Modify: `feature/reports/src/main/kotlin/com/redmiklab/reports/RoomReportExporter.kt`
- Test: `feature/reports/src/test/kotlin/com/redmiklab/reports/ReportZipExporterTest.kt`

**Interfaces:**
- Produces: UI labels `标准模式`, `严格模式`, and separate sampling interval input.
- Produces: `RuntimeModeUiPolicy.status(settings, pending, activeRun)`.

- [ ] **Step 1: Write failing UI and timeout report tests**

```kotlin
@Test fun activeRunModeIsShownSeparatelyFromNextSetting() {
    val status = policy.status(next = STRICT, active = STANDARD)
    assertEquals("本次运行：标准模式；下一次设置：严格模式", status)
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :app:testDebugUnitTest :feature:reports:testDebugUnitTest`

Expected: missing policy and report timing fields.

- [ ] **Step 3: Implement UI, validation and `onTimeout`**

Add mode controls, editable sampling interval, strict prerequisites and warnings. `onTimeout` records a `DATA_SYNC_TIMEOUT` event and calls `stopSelf()` immediately after safe persistence.

Export Run mode, intervals, planned/actual snapshot times, delay and diagnostic events.

- [ ] **Step 4: Run tests, lint, build and commit**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`

Expected: build succeeds with zero failing tests and zero lint errors.

Commit: `feat: expose runtime modes and timeout diagnostics`

### Task 7: Runtime-mode device verification

**Files:**
- Create: `docs/superpowers/progress/2026-07-16/runtime-modes.md`

- [ ] **Step 1: Install the debug APK on K80**

Run: `/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: `Success`.

- [ ] **Step 2: Execute accelerated standard and strict runs**

Use a short window and temporary small intervals without changing production defaults. Verify standard actions stop after completion, strict mode holds the VPN/wake lock, both finalize, and repeated start/end actions remain idempotent.

- [ ] **Step 3: Capture system evidence**

Record `dumpsys activity services`, `dumpsys alarm`, exported ZIP timing fields and absence of a continuously running `NightDiagnosticService`.

- [ ] **Step 4: Document and commit**

Commit: `test: verify runtime modes on K80`
