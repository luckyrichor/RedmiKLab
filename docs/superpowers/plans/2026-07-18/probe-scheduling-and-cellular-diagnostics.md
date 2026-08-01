# Probe Scheduling and Cellular Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record actionable cellular-network resolution evidence and trigger strict-mode probes from the awake in-service deadline scheduler instead of relying exclusively on delayed system alarms.

**Architecture:** Extend the cellular resolver with immutable diagnostic snapshots and a resolution trace emitted to diagnostic events. Extend strict-mode coordination with a separate fixed-rate probe schedule while retaining AlarmManager as fallback, and make probe persistence idempotent by planned time.

**Tech Stack:** Kotlin, Java, Android ConnectivityManager, Room, AlarmManager, Handler, JUnit, Robolectric, Gradle.

## Global Constraints

- The same APK supports Redmi K60 and K80.
- Strict mode continues to require connection-level capture and its bounded wake lock.
- Standard-mode scheduling remains AlarmManager-based.
- Probe endpoints, retry counts, retry delay, per-attempt limit and nightly budget do not change.
- Production changes follow red-green-refactor and independently verified changes receive Git commits.

---

### Task 1: Cellular resolution diagnostic trace

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/CellularNetworkDiagnostic.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/AndroidCellularNetworkResolver.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/MobileHttpProbeRunner.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/NightDiagnosticService.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/CellularNetworkDiagnosticTest.kt`

- [ ] Write a failing test for stable JSON rendering of active-network state, candidate capabilities, selected network and resolution outcome.
- [ ] Run the focused test and confirm it fails because the diagnostic model does not exist.
- [ ] Implement immutable network snapshots, resolution outcomes and JSON rendering.
- [ ] Emit `PROBE_NETWORK_DIAGNOSTIC` events for every probe attempt.
- [ ] Run focused and app unit tests and commit the independently verified change.

### Task 2: Strict-mode fixed-rate probe scheduling

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/StrictSamplingCoordinator.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ConnectionCaptureVpnService.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/NightDiagnosticService.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/StrictSamplingCoordinatorTest.kt`

- [ ] Write failing tests proving strict mode emits probes at absolute throughput slots and skips past slots after recovery.
- [ ] Run the focused tests and confirm the missing probe callback failure.
- [ ] Add the independent fixed-rate probe schedule and pass throughput interval through strict attachment.
- [ ] Keep AlarmManager probe scheduling as fallback and run all app tests.
- [ ] Commit the independently verified scheduling change.

### Task 3: Probe persistence idempotency and report fields

**Files:**
- Modify: `core/storage/src/main/java/com/redmiklab/storage/ProbeEntity.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDao.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDatabase.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDatabaseFactory.java`
- Modify: `core/storage/src/test/kotlin/com/redmiklab/storage/DiagnosticDaoTest.kt`
- Modify: `feature/reports/src/main/kotlin/com/redmiklab/reports/RoomReportExporter.kt`
- Test: `feature/reports/src/test/kotlin/com/redmiklab/reports/RoomReportExporterTest.kt`

- [ ] Write failing DAO and report tests for planned time, delay, trigger source and schedule lookup.
- [ ] Run focused tests and confirm compilation/schema failures.
- [ ] Add schema version 12 and migration 11→12, then reject already-completed probe schedules before network work begins.
- [ ] Export planned time, actual time, delay and trigger source in `probes.csv`.
- [ ] Run storage and report tests and commit the independently verified change.

### Task 4: Full verification and K80 installation

**Files:**
- Create: `docs/superpowers/progress/2026-07-18/probe-scheduling-and-cellular-diagnostics.md`

- [ ] Run `./gradlew test lintDebug assembleDebug` and record exact results.
- [ ] Verify the connected device is K80, install the debug APK and launch the activity.
- [ ] Confirm the process remains alive and the existing database opens after migration.
- [ ] Record evidence, commit documentation and confirm a clean worktree.
