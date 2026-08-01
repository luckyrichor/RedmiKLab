# Physical Cellular Candidate Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure active probes bind only to a verified physical cellular data network and never to VPN or carrier-specialized networks.

**Architecture:** Convert Android capabilities into a pure candidate-facts model, evaluate it with an explicit rejection reason, and record the physical cellular network selected when RedmiKLab creates its VPN. The resolver uses that session network ID as the only permitted no-`INTERNET` compatibility fallback and exposes every decision in diagnostic JSON.

**Tech Stack:** Kotlin, Android ConnectivityManager/VpnService, SharedPreferences, JUnit, Robolectric, Gradle.

## Global Constraints

- The same APK supports Redmi K60 and K80.
- VPN, IMS, EIMS and MMS networks are never active-probe candidates.
- A network without `INTERNET` is accepted only when the active default is a validated VPN and its ID matches the current RedmiKLab VPN session underlying-network ID.
- No interface-name heuristic, hidden API, reflection or Root access is permitted.
- Existing endpoints, 5+5 retries, 10-second retry delay, 5 MB attempt cap and 1 GB nightly budget remain unchanged.
- Production changes follow red-green-refactor and receive a Git commit after focused verification.

---

### Task 1: Pure physical-cellular candidate decisions

**Files:**
- Modify: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/CellularNetworkCandidatePolicy.kt`
- Modify: `feature/diagnostics/src/test/kotlin/com/redmiklab/diagnostics/CellularNetworkCandidatePolicyTest.kt`

**Interfaces:**
- Produces: `CellularNetworkCandidateFacts`, `CellularCandidateRejection`, `CellularNetworkCandidateDecision`, and `CellularNetworkCandidatePolicy.evaluate(facts, activeDefaultIsValidatedVpn)`.
- Consumed by: Android resolver and VPN underlying-network selection in Tasks 2 and 3.

- [x] **Step 1: Write failing policy tests**

Add tests that construct facts such as:

```kotlin
private fun facts(
    isVpn: Boolean = false,
    isIms: Boolean = false,
    isEims: Boolean = false,
    isMms: Boolean = false,
    hasInternet: Boolean = true,
    isRecordedUnderlying: Boolean = false,
) = CellularNetworkCandidateFacts(
    isCellular = true,
    isVpn = isVpn,
    isIms = isIms,
    isEims = isEims,
    isMms = isMms,
    hasInternet = hasInternet,
    isValidated = true,
    isNotSuspended = true,
    isRecordedUnderlying = isRecordedUnderlying,
)
```

Assert that VPN, IMS, EIMS and MMS facts return `score=null` with the corresponding rejection; unvalidated and suspended facts are rejected; normal physical Internet facts are accepted; and a no-Internet fact is accepted only when both `activeDefaultIsValidatedVpn=true` and `isRecordedUnderlying=true`.

- [x] **Step 2: Run the focused test and confirm RED**

Run:

```text
./gradlew :feature:diagnostics:testDebugUnitTest --tests com.redmiklab.diagnostics.CellularNetworkCandidatePolicyTest
```

Expected: compilation failure because the new facts and decision types do not exist.

- [x] **Step 3: Implement the minimal pure policy**

Use these stable interfaces:

```kotlin
data class CellularNetworkCandidateFacts(
    val isCellular: Boolean,
    val isVpn: Boolean,
    val isIms: Boolean,
    val isEims: Boolean,
    val isMms: Boolean,
    val hasInternet: Boolean,
    val isValidated: Boolean,
    val isNotSuspended: Boolean,
    val isRecordedUnderlying: Boolean,
)

enum class CellularCandidateRejection {
    NOT_CELLULAR, VPN_TRANSPORT, IMS_CAPABILITY, EIMS_CAPABILITY, MMS_CAPABILITY,
    NOT_VALIDATED, SUSPENDED, INTERNET_MISSING_UNTRUSTED,
}

data class CellularNetworkCandidateDecision(val score: Int?, val rejection: CellularCandidateRejection?)
```

Normal physical Internet candidates score above the recorded no-Internet compatibility fallback. A recorded underlying network receives a same-tier preference boost. Every rejected candidate has exactly one deterministic rejection value.

- [x] **Step 4: Run the focused test and confirm GREEN**

Run the command from Step 2. Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Commit the policy**

```text
git add feature/diagnostics
git commit -m "fix: reject non-physical cellular probe candidates"
```

### Task 2: Record the RedmiKLab VPN session underlying network

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/UnderlyingCellularSessionStore.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/PhysicalCellularNetworkSelector.kt`
- Create: `app/src/test/kotlin/com/redmiklab/app/UnderlyingCellularSessionStoreTest.kt`
- Create: `app/src/test/kotlin/com/redmiklab/app/PhysicalCellularNetworkSelectorTest.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ConnectionCaptureVpnService.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/ConnectionCaptureVpnServiceTest.kt`

**Interfaces:**
- Consumes: `CellularNetworkCandidatePolicy.evaluate` from Task 1.
- Produces: `UnderlyingCellularSessionStore.read(): String?`, `record(networkId: String)`, `clear()`, and `PhysicalCellularNetworkSelector.select(candidates, activeDefaultIsValidatedVpn)`.
- Consumed by: `MobileHttpProbeRunner` and `AndroidCellularNetworkResolver` in Task 3.

- [x] **Step 1: Write failing store and selection tests**

Test that `record("218")` survives a new store instance and `clear()` returns the store to null. Test `PhysicalCellularNetworkSelector` with `(networkId, value, facts)` candidates and prove it chooses an accepted physical candidate while rejecting VPN/IMS candidates regardless of list order. Extend the Robolectric service test so `onDestroy()` clears a pre-recorded ID, proving the common stop/failure cleanup path cannot leave stale identity.

- [x] **Step 2: Run tests and confirm RED**

```text
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.UnderlyingCellularSessionStoreTest --tests com.redmiklab.app.PhysicalCellularNetworkSelectorTest --tests com.redmiklab.app.ConnectionCaptureVpnServiceTest
```

Expected: compilation failure because the session store does not exist.

- [x] **Step 3: Implement session-scoped persistence and VPN binding**

Implement `UnderlyingCellularSessionStore` on the existing `connection_capture` SharedPreferences with key `underlying_cellular_network_id`. In `startCapture()`:

```kotlin
val underlying = safePhysicalCellularNetwork()
if (underlying != null) builder.setUnderlyingNetworks(arrayOf(underlying))
```

Record `underlying.toString()` only after `builder.establish()` and the native forwarder both succeed. Clear the ID before a new start attempt and in `stopCapture()`, including establish/forwarder failure cleanup.

- [x] **Step 4: Run focused App tests and confirm GREEN**

Run the command from Step 2. Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Commit the VPN session identity change**

```text
git add app/src/main app/src/test
git commit -m "fix: record VPN underlying cellular session"
```

### Task 3: Resolve and report only safe physical candidates

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/AndroidCellularNetworkResolver.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/AndroidCellularCandidateFactsFactory.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/CellularNetworkDiagnostic.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/MobileHttpProbeRunner.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/CellularNetworkDiagnosticTest.kt`
- Create: `app/src/test/kotlin/com/redmiklab/app/AndroidCellularCandidateFactsFactoryTest.kt`

**Interfaces:**
- Consumes: policy decision types from Task 1 and `UnderlyingCellularSessionStore.read()` from Task 2.
- Produces: a resolver whose selected network is never VPN/IMS/EIMS/MMS and diagnostic JSON containing the session underlying ID, per-candidate match flag and rejection reason.

- [x] **Step 1: Write failing resolver and JSON tests**

Use `NetworkCapabilities.Builder` to test that the Android facts factory maps inherited `CELLULAR + VPN`, IMS/EIMS/MMS and validation capabilities correctly. Extend diagnostic rendering tests so JSON contains `recordedUnderlyingNetworkId`, `isRecordedUnderlying` and `rejectionReason`.

The selector tests from Task 2 must already prove scenarios where:

- a physical network and `CELLULAR + VPN` network have equal Internet capabilities, but physical is selected;
- an IMS network and recorded no-Internet physical network coexist, but only the recorded network is selected;
- an unrecorded no-Internet network produces no selection;
- JSON contains `recordedUnderlyingNetworkId`, `isRecordedUnderlying` and `rejectionReason`.

Keep final Android `Network` enumeration in the resolver; do not mock final framework network identity behavior in JVM tests.

- [x] **Step 2: Run focused tests and confirm RED**

```text
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.AndroidCellularCandidateFactsFactoryTest --tests com.redmiklab.app.CellularNetworkDiagnosticTest
```

Expected: compilation or assertion failure because the safe-selection diagnostics are absent.

- [x] **Step 3: Wire Android capabilities into the pure policy**

For every `Network`, pass `TRANSPORT_CELLULAR`, `TRANSPORT_VPN`, `NET_CAPABILITY_IMS`, `EIMS`, `MMS`, `INTERNET`, `VALIDATED`, `NOT_SUSPENDED`, and the session-ID match into `CellularNetworkCandidateFacts`. Use `decision.score` for selection and expose `decision.rejection?.name` in the snapshot.

Add `CellularResolutionOutcome.NO_SAFE_PHYSICAL_CELLULAR`. When the request path ends without an accepted network, report this outcome rather than a misleading DNS/connection diagnosis. `MobileHttpProbeRunner` supplies `UnderlyingCellularSessionStore(context).read()` to the resolver.

- [x] **Step 4: Run all focused tests and confirm GREEN**

```text
./gradlew :feature:diagnostics:testDebugUnitTest :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Commit resolver and diagnostic changes**

```text
git add app feature/diagnostics
git commit -m "fix: bind probes to verified physical cellular network"
```

### Task 4: Full verification and K80 rollout

**Files:**
- Create: `docs/superpowers/progress/2026-07-19/physical-cellular-candidate-hardening.md`
- Modify: `docs/superpowers/plans/2026-07-19/physical-cellular-candidate-hardening.md`

**Interfaces:**
- Consumes: the complete implementation from Tasks 1–3.
- Produces: verified APK, K80 installation evidence and a clean Git worktree.

- [x] **Step 1: Run full automated verification**

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew test lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`; no unit-test or Lint failure.

- [x] **Step 2: Verify safe installation state**

Use ADB to confirm model `24117RK2CC`/device `zorn` and query the Room database for `RUNNING` or `FINALIZING`. Do not install while either status exists.

- [x] **Step 3: Install and verify migration/startup**

Run `adb install -r app/build/outputs/apk/debug/app-debug.apk`, launch the Activity, confirm a persistent PID and an empty current AndroidRuntime crash buffer. Verify existing run/snapshot/probe counts are unchanged.

- [x] **Step 4: Perform a bright-screen VPN probe**

Start a short strict diagnostic with RedmiKLab connection capture enabled. Trigger through the App-owned private alarm path. Verify diagnostic JSON selects the physical network, assigns null score plus explicit rejection to VPN and IMS candidates, and records a successful probe or a genuine endpoint-stage failure.

- [x] **Step 5: Record evidence and commit**

Write exact commands/results, APK hash, device identity, database counts and the remaining lock-screen acceptance item to the progress document. Mark every completed plan checkbox, run `git diff --check`, commit with `docs: verify physical cellular candidate rollout`, and confirm `git status --short` is empty.
