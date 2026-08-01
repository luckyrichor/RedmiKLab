# Probe VPN Compatibility and Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make strict-mode probes usable beneath the local VPN, persist correct round timing, and prevent long retries from blocking snapshots or finalization.

**Architecture:** Add a VPN-aware cellular candidate policy, persist the complete probe round lifecycle in Room schema 13, and route probe commands to a dedicated short-lived foreground service while keeping lifecycle actions in the existing serialized service.

**Tech Stack:** Kotlin, Java, Android ConnectivityManager, Room, Foreground Service, AlarmManager, Handler, JUnit, Robolectric, Gradle.

## Global Constraints

- The same APK supports Redmi K60 and K80.
- The fallback accepts a cellular network without `INTERNET` only when the active default is a validated VPN and the cellular network is validated and not suspended.
- Probe endpoints, 5+5 retries, 10-second delay, 5 MiB attempt cap and 1 GiB nightly budget remain unchanged.
- Production changes follow red-green-refactor and receive Git commits after focused verification.

---

### Task 1: VPN-aware cellular fallback

**Files:**
- Modify: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/CellularNetworkCandidatePolicy.kt`
- Modify: `feature/diagnostics/src/test/kotlin/com/redmiklab/diagnostics/CellularNetworkCandidatePolicyTest.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/AndroidCellularNetworkResolver.kt`

- [x] Add failing policy tests for a validated VPN default with a validated, non-suspended cellular network lacking `INTERNET` and for rejection without a VPN default.
- [x] Run the focused diagnostics test and confirm RED.
- [x] Implement the guarded fallback and retain higher scoring for normal Internet-capable cellular networks.
- [x] Run diagnostics and app tests, then commit.

### Task 2: Correct probe round lifecycle timing

**Files:**
- Modify: `core/storage/src/main/java/com/redmiklab/storage/ProbeEntity.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDatabase.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDatabaseFactory.java`
- Modify: `core/storage/src/test/kotlin/com/redmiklab/storage/DiagnosticDaoTest.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/ProbeRoundTimeline.kt`
- Create: `app/src/test/kotlin/com/redmiklab/app/ProbeRoundTimelineTest.kt`
- Modify: `feature/reports/src/main/kotlin/com/redmiklab/reports/ProbeCsvRenderer.kt`
- Modify: `feature/reports/src/test/kotlin/com/redmiklab/reports/ProbeCsvRendererTest.kt`

- [x] Add failing timeline, DAO and CSV tests for dispatch, first attempt, last attempt and completion times.
- [x] Run focused tests and confirm RED.
- [x] Add schema 13 and migration 12→13, then persist the round lifecycle without deriving dispatch time from the final attempt.
- [x] Export the complete timeline and run focused tests.
- [x] Commit the verified timing change.

### Task 3: Isolate probes from lifecycle actions

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/DiagnosticServiceRouter.kt`
- Create: `app/src/test/kotlin/com/redmiklab/app/DiagnosticServiceRouterTest.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/NightProbeService.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/NightDiagnosticService.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticAlarmReceiver.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ConnectionCaptureVpnService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [x] Add a failing routing test proving only `ACTION_PROBE` targets `NightProbeService`.
- [x] Run the focused app test and confirm RED.
- [x] Extract probe execution into the dedicated service while preserving schedule dedupe and fallback alarm scheduling.
- [x] Run all app tests and commit.

### Task 4: Full verification and K80 installation

**Files:**
- Create: `docs/superpowers/progress/2026-07-19/probe-vpn-compatibility-and-isolation.md`

- [x] Run `./gradlew test lintDebug assembleDebug` and record the exact result.
- [x] Confirm the connected device is K80 and no diagnostic run is active.
- [x] Install with `adb install -r`, launch the App and check the crash buffer.
- [x] Verify database version 13 and unchanged historical run, snapshot and probe counts.
- [x] Commit verification evidence and confirm a clean worktree.
