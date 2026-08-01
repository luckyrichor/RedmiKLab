# Six-Hour Diagnostics and Connection Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Safely complete and export six-hour mobile diagnostics, make probe failures explainable, and add an opt-in transparent connection-capture mode.

**Architecture:** Persist diagnostic lifecycle state in Room so service restarts and end receivers can reconcile any active run. Keep the foreground service below its Android `dataSync` limit with a 120-second completion margin. Model probe attempts separately from final probe results. The connection-capture module must be a transparent local VPN that forwards traffic while recording metadata.

**Tech Stack:** Kotlin, Android `AlarmManager`, `Service`, Room, `Network`, `VpnService`, JUnit, Robolectric.

## Global Constraints

- Validate every user-selected window as no longer than 6 hours, including midnight crossing.
- For a 6-hour window, final capture starts 120 seconds before the selected end.
- No traffic capture may interrupt normal phone networking.
- Connection capture remains opt-in and conflicts with an active Clash Meta VPN.
- Tests precede production changes; commits follow independently testable deliverables.

---

### Task 1: Window guard and safe finalization

**Files:** `core/model/.../DiagnosticConfig.kt`, `feature/diagnostics/.../DiagnosticWindow.kt`, their tests, `app/.../MainActivity.kt`, `app/.../NightDiagnosticService.kt`.

- [x] Write failing tests for six-hour acceptance, six-hour-plus rejection, and a 120-second safe completion deadline.
- [x] Implement maximum-window validation and bounded 120-second safe completion.
- [x] Apply validation in time selection and invoke final capture at the safe deadline.
- [x] Run model and app tests and commit the window guard.

### Task 2: Persisted lifecycle reconciliation

**Files:** `core/storage/.../DiagnosticDao.java`, `DiagnosticRunEntity.java`, Room tests, `app/.../NightDiagnosticService.kt`, `DiagnosticAlarmReceiver.kt`, exporter.

- [x] Write failing DAO tests for finding an active run, completing it, and interrupting it without a final snapshot.
- [x] Add terminal run state and persisted active-run lookup.
- [x] Make manual stop and end alarms reconcile database state even when the original service process no longer exists.
- [x] Allow export of terminal interrupted records with a report warning.
- [x] Run storage/report tests and commit reliable lifecycle reconciliation.

### Task 3: Probe endpoint and attempt audit

**Files:** `core/model/.../DiagnosticConfig.kt`, `app/.../DiagnosticPreferences.kt`, `MobileHttpProbeRunner.kt`, `FailoverProbeRunner.kt`, storage schema/DAO, report exporter, tests.

- [x] Write failing tests for domestic defaults, mobile-network DNS selection, and ordered attempt audit records.
- [x] Update default endpoints and bind DNS lookup to the usable cellular Internet `Network`.
- [x] Persist each primary/fallback attempt with index, phase, endpoint, timing, and failure.
- [x] Render an attempt table in HTML and CSV while preserving final result summaries.
- [x] Run app/storage/report tests and commit probe attempt auditing.

### Task 4: Opt-in transparent connection capture

**Files:** connection-capture module, `AndroidManifest.xml`, `MainActivity.kt`, Room schema/DAO, report renderer, Android tests.

- [x] Define failing pure tests for activity classification from screen state, foreground package, and captured flow metadata.
- [x] Implement the local VPN permission and lifecycle UI with Clash/other-VPN conflict handling.
- [x] Integrate a dual-stack transparent forwarding engine and persist connection metadata without payload capture.
- [x] Record screen/foreground context and render evidence-level classifications.
- [x] Verify K80 normal networking with capture enabled and disabled and commit the connection-capture implementation.

### Task 5: End-to-end verification and installation

**Files:** all affected modules and dated docs.

- [x] Run full `testDebugUnitTest`, `lintDebug`, and `assembleDebug` checks.
- [x] Install on K80 with ADB, launch, and verify settings, services and dual-stack VPN.
- [x] Run K80 short real-device diagnostics and confirm terminal export behavior.
- [x] Commit K80 corrections and document the validation result.
- [ ] Connect K60, install the exact same APK, and execute the full acceptance matrix in `docs/acceptance/2026-07-13/k60-k80-night-run.md`.
- [ ] Record K60 evidence, confirm no required gap remains, and complete the long-running goal.
