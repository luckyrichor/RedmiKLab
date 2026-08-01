# K60 Runtime Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the six runtime defects demonstrated by the K60 overnight report, verify the complete Android project, and install the resulting APK on the connected K60.

**Architecture:** Keep the existing persisted short-action architecture. Add an atomic finalization claim, a shared cellular-network resolver, fixed-rate strict-mode deadlines with an in-service end fallback, and lossless accounting for partial probe downloads.

**Tech Stack:** Kotlin, Java, Android 15 APIs, Room, AlarmManager, VpnService, Handler/elapsed realtime, JUnit, Robolectric, Gradle.

## Global Constraints

- The same APK must support Redmi K60 and K80.
- Strict mode continues to require connection-level capture and uses its existing bounded partial wake lock.
- System exact alarms remain enabled as recovery fallbacks.
- Probe endpoints, 5+5 retries, 10-second delay, 5 MiB per attempt and 1 GiB nightly budget remain unchanged.
- Production changes follow red-green-refactor and each independently verified change receives a Git commit.

---

### Task 1: Atomic run finalization

**Files:**
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDao.java`
- Modify: `core/storage/src/test/kotlin/com/redmiklab/storage/DiagnosticDaoTest.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/NightDiagnosticService.kt`
- Create: `app/src/test/kotlin/com/redmiklab/app/RunFinalizationPolicyTest.kt`

**Interfaces:**
- Produces: `claimRunForFinalization(runId, claimedAtEpochMs): Int` (`1` for the sole winner, `0` otherwise).
- Produces: `completeFinalizingRun(runId, endedAtEpochMs, reason): Int`.
- Produces: `recoverableRun(): DiagnosticRunEntity?` for `RUNNING` or `FINALIZING` state.

- [ ] **Step 1: Write failing DAO and policy tests**

```kotlin
@Test fun only_one_finalizer_can_claim_a_running_run() {
    dao.insertRun(runningRun())
    assertEquals(1, dao.claimRunForFinalization(RUN_ID, 7_000))
    assertEquals(0, dao.claimRunForFinalization(RUN_ID, 7_001))
    assertEquals("FINALIZING", dao.runById(RUN_ID)!!.status)
}

@Test fun a_finalizing_run_remains_recoverable_after_process_restart() {
    dao.insertRun(runningRun())
    dao.claimRunForFinalization(RUN_ID, 7_000)
    assertEquals(RUN_ID, dao.recoverableRun()!!.runId)
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew :core:storage:testDebugUnitTest --tests com.redmiklab.storage.DiagnosticDaoTest`

Expected: compilation fails because the claim and recovery DAO methods do not exist.

- [ ] **Step 3: Implement the atomic state transition and serialized service executor**

Use conditional Room updates `RUNNING -> FINALIZING -> COMPLETED`. Only the claim winner writes `FINAL_END`, prunes evidence, writes `RUN_COMPLETED`, cancels alarms and detaches strict sampling. Recovery resumes a `FINALIZING` run. Replace one `Thread` per command with one service-owned single-thread executor and close it from `onDestroy`.

- [ ] **Step 4: Run storage and app tests**

Run: `./gradlew :core:storage:testDebugUnitTest :app:testDebugUnitTest`

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/storage app/src/main app/src/test
git commit -m "fix: finalize diagnostic runs exactly once"
```

### Task 2: Resolve the underlying cellular network beneath a local VPN

**Files:**
- Create: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/CellularNetworkCandidatePolicy.kt`
- Create: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/AndroidCellularNetworkResolver.kt`
- Create: `feature/diagnostics/src/test/kotlin/com/redmiklab/diagnostics/CellularNetworkCandidatePolicyTest.kt`
- Modify: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/AndroidNetworkSnapshotSource.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/MobileHttpProbeRunner.kt`
- Remove: `app/src/main/kotlin/com/redmiklab/app/MobileNetworkCapabilityPolicy.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/MobileNetworkCapabilityPolicyTest.kt`

**Interfaces:**
- Produces: `CellularNetworkCandidatePolicy.score(isCellular, hasInternet, isValidated, isNotSuspended): Int?`.
- Produces: `AndroidCellularNetworkResolver.current(): Network?` and `await(timeoutMs): Network?`.

- [ ] **Step 1: Write failing candidate-selection tests**

```kotlin
@Test fun validated_cellular_is_preferred_over_unvalidated_cellular() {
    assertTrue(policy.score(true, true, true, true)!! > policy.score(true, true, false, true)!!)
}

@Test fun an_unvalidated_cellular_internet_network_remains_a_fallback() {
    assertNotNull(policy.score(true, true, false, true))
}

@Test fun vpn_and_wifi_are_not_cellular_candidates() {
    assertNull(policy.score(false, true, true, true))
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :feature:diagnostics:testDebugUnitTest`

Expected: compilation fails because the policy and resolver do not exist.

- [ ] **Step 3: Implement candidate scoring, enumeration and bounded network request**

Enumerate `ConnectivityManager.allNetworks`, score cellular Internet networks, and prefer validated/non-suspended candidates. When no candidate exists, issue a callback-based cellular `NetworkRequest` with a bounded wait and always unregister the callback. `AndroidNetworkSnapshotSource` uses the resolver rather than `activeNetwork`; `MobileHttpProbeRunner` uses `current() ?: await(...)`.

- [ ] **Step 4: Run diagnostics and app tests**

Run: `./gradlew :feature:diagnostics:testDebugUnitTest :app:testDebugUnitTest`

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add feature/diagnostics app/src/main app/src/test
git commit -m "fix: resolve cellular transport beneath local VPN"
```

### Task 3: Preserve partial bytes from failed probes

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/BoundedDownloadReader.kt`
- Create: `app/src/test/kotlin/com/redmiklab/app/BoundedDownloadReaderTest.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/MobileHttpProbeRunner.kt`

**Interfaces:**
- Produces: `BoundedDownloadResult(bytesRead: Long, failure: Throwable?)`.
- Produces: `BoundedDownloadReader.read(input, maximumBytes): BoundedDownloadResult`.

- [ ] **Step 1: Write the failing partial-read test**

```kotlin
@Test fun reports_bytes_received_before_the_stream_failed() {
    val input = object : InputStream() {
        var reads = 0
        override fun read(): Int = if (reads++ < 3) 1 else throw IOException("reset")
    }
    val result = BoundedDownloadReader.read(input, 5)
    assertEquals(3, result.bytesRead)
    assertTrue(result.failure is IOException)
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.BoundedDownloadReaderTest`

Expected: compilation fails because the reader does not exist.

- [ ] **Step 3: Implement the reader and use its byte count on failure**

Keep DNS and HTTPS timings already reached. If reading fails, return `ProbeFailure.Timeout` or `ProbeFailure.Connection` with the reader's actual bytes and no throughput result. Ensure `HttpsURLConnection.disconnect()` runs in `finally`.

- [ ] **Step 4: Run probe tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.redmiklab.app.*Probe*' --tests com.redmiklab.app.BoundedDownloadReaderTest`

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "fix: account for partial failed probe downloads"
```

### Task 4: Fixed-rate strict sampling and in-service final deadlines

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/StrictSamplingCoordinator.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ConnectionCaptureVpnService.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/StrictSamplingCoordinatorTest.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/FixedRateDeadlinePlanner.kt`
- Create: `app/src/test/kotlin/com/redmiklab/app/FixedRateDeadlinePlannerTest.kt`

**Interfaces:**
- Extends: `StrictSamplingTicker.scheduleOnce(delayMs, task)` plus fixed-rate periodic scheduling.
- Extends: strict sink with `safeCheckpoint(runId, plannedAt)` and `finish(runId, plannedAt)`.
- Produces: absolute elapsed-realtime deadlines based on the original first deadline.

- [ ] **Step 1: Write failing fixed-rate and final-deadline tests**

```kotlin
@Test fun task_runtime_does_not_shift_the_next_deadline() {
    val planner = FixedRateDeadlinePlanner(firstDeadlineMs = 300_000, intervalMs = 300_000)
    assertEquals(300_000, planner.next())
    assertEquals(600_000, planner.next())
}

@Test fun strict_mode_schedules_checkpoint_and_end_inside_the_awake_service() {
    coordinator.start("run", 0, 0, 300_000, 1_800_000)
    ticker.fireAt(1_680_000)
    ticker.fireAt(1_800_000)
    assertEquals(listOf(1_680_000L), sink.safeCheckpoints)
    assertEquals(listOf(1_800_000L), sink.finishes)
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.StrictSamplingCoordinatorTest --tests com.redmiklab.app.FixedRateDeadlinePlannerTest`

Expected: missing one-shot ticker, sink actions and planner.

- [ ] **Step 3: Implement absolute Handler deadlines and strict finalization callbacks**

Use `SystemClock.uptimeMillis()` with `Handler.postAtTime`, advancing the deadline from its original base. Schedule safe checkpoint at `end - 120 seconds` when still future and end at the exact planned deadline. Keep AlarmManager schedules unchanged as backup. Stop and clear all strict callbacks after end or detach.

- [ ] **Step 4: Run all app tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: strict lifecycle, wake-lock, service and existing tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "fix: keep strict sampling and finalization on fixed deadlines"
```

### Task 5: Full verification, documentation and K60 installation

**Files:**
- Create: `docs/superpowers/progress/2026-07-17/runtime-hardening-verification.md`

- [ ] **Step 1: Run the complete verification suite**

Run: `./gradlew test lintDebug assembleDebug`

Expected: `BUILD SUCCESSFUL` with all unit tests and lint tasks passing and `app-debug.apk` produced.

- [ ] **Step 2: Verify the connected device and install**

Run: `/opt/homebrew/share/android-commandlinetools/platform-tools/adb devices -l`

Expected: serial `17ead82e`, model `23013RK75C`, state `device`.

Run: `/opt/homebrew/share/android-commandlinetools/platform-tools/adb -s 17ead82e install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: `Success`.

- [ ] **Step 3: Launch and inspect immediate crashes**

Run: `/opt/homebrew/share/android-commandlinetools/platform-tools/adb -s 17ead82e shell am start -W -n com.redmiklab.app/.MainActivity`

Expected: Activity launch succeeds.

Run: `/opt/homebrew/share/android-commandlinetools/platform-tools/adb -s 17ead82e shell pidof com.redmiklab.app`

Expected: a live process ID.

- [ ] **Step 4: Record exact verification evidence and commit**

```bash
git add docs/superpowers/progress/2026-07-17/runtime-hardening-verification.md
git commit -m "docs: verify K60 runtime hardening"
```

- [ ] **Step 5: Confirm the worktree is clean**

Run: `git status --short`

Expected: no output.
