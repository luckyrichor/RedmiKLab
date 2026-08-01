# Media Notification and Endpoint Correlation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add opt-in media/notification evidence, visible hostname observations and explainable connection classifications without HTTPS decryption or Root.

**Architecture:** A notification listener records system-exposed notification and media events for an active Run. Connection capture adds DNS/SNI hostname observations and classifies endpoints. Export filters notification data to network-participating or media-related packages, then correlates endpoint, playback, screen and foreground evidence into confidence-scored findings.

**Tech Stack:** Kotlin, Android NotificationListenerService/MediaSessionManager, Room, native C++ forwarding metadata hooks, JUnit, Robolectric, CTest.

## Global Constraints

- Media/notification access remains optional; denial never blocks basic diagnosis.
- Export full system-readable notification fields only for network-participating and media-related Apps.
- Foreground-only context Apps export package, label and time only.
- Do not decrypt HTTPS, capture packet payloads, record credentials, or require Root.
- Preserve import compatibility with old report ZIPs.
- Tests precede production changes.

---

### Task 1: Persist notification, media, endpoint and context evidence

**Files:**
- Create: `core/storage/src/main/java/com/redmiklab/storage/NotificationEventEntity.java`
- Create: `core/storage/src/main/java/com/redmiklab/storage/MediaEventEntity.java`
- Create: `core/storage/src/main/java/com/redmiklab/storage/EndpointObservationEntity.java`
- Create: `core/storage/src/main/java/com/redmiklab/storage/ActivityContextEntity.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/ConnectionFlowEntity.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDatabase.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDatabaseFactory.java`
- Modify: `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDao.java`
- Test: `core/storage/src/test/kotlin/com/redmiklab/storage/DiagnosticDaoTest.kt`

**Interfaces:**
- Produces: Room schema version 10.
- Produces: DAO queries for events by Run and participating package.
- Produces: `networkParticipantPackages(runId)` as the union of flows and app traffic.

- [ ] **Step 1: Write failing DAO tests**

```kotlin
@Test fun participantPackagesUnionFlowsAndTraffic() {
    dao.insertConnectionFlow(flow("com.video"))
    dao.insertAppTraffic(traffic("com.browser"))
    assertEquals(setOf("com.video", "com.browser"), dao.networkParticipantPackages(RUN_ID).toSet())
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :core:storage:testDebugUnitTest`

Expected: missing entities and DAO APIs.

- [ ] **Step 3: Add entities and migration 9 to 10**

Notification rows store all normalized text/extras fields and a `pendingRelevance` flag. Media rows store playback state and metadata. Endpoint rows store hostname, source, IP and category. Activity context rows store foreground intervals.

Connection flows add nullable hostname, hostname source and endpoint category.

- [ ] **Step 4: Run storage tests and commit**

Run: `./gradlew :core:storage:testDebugUnitTest`

Expected: all storage tests pass.

Commit: `feat: persist media notification and endpoint evidence`

### Task 2: Full notification capture with relevance filtering

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/DiagnosticNotificationListener.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/NotificationFieldExtractor.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/NotificationRelevancePolicy.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/kotlin/com/redmiklab/app/NotificationFieldExtractorTest.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/NotificationRelevancePolicyTest.kt`

**Interfaces:**
- Produces: `NotificationFieldExtractor.extract(StatusBarNotification): CapturedNotification`.
- Produces: `NotificationRelevancePolicy.keep(packageName, networkPackages, mediaPackages, foregroundOnlyPackages)`.

- [ ] **Step 1: Write failing extraction and filtering tests**

```kotlin
@Test fun extractsExpandedAndConversationText() {
    val captured = extractor.extract(notificationWith(title = "标题", bigText = "完整正文"))
    assertEquals("完整正文", captured.bigText)
}

@Test fun foregroundOnlyPackageIsNotExportRelevant() {
    assertFalse(policy.keep("com.calculator", emptySet(), emptySet(), setOf("com.calculator")))
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :app:testDebugUnitTest`

Expected: missing listener, extractor and policy.

- [ ] **Step 3: Implement listener and normalized extras extraction**

Register a non-exported `NotificationListenerService`. During an active Run, persist readable fields as pending. On Run finalization, retain rows for the union of network and media packages and delete unrelated pending rows.

- [ ] **Step 4: Run app tests and commit**

Run: `./gradlew :app:testDebugUnitTest`

Expected: notification extraction and relevance tests pass.

Commit: `feat: capture relevant full notification fields`

### Task 3: Active media-session timeline

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/MediaSessionCollector.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/MediaMetadataExtractor.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/MediaMetadataExtractorTest.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticNotificationListener.kt`

**Interfaces:**
- Produces: media event callbacks keyed by package and session token hash.
- Produces: normalized states `PLAYING`, `PAUSED`, `BUFFERING`, `STOPPED`, `OTHER`.

- [ ] **Step 1: Write failing media normalization tests**

```kotlin
@Test fun playingSessionPreservesTitleArtistAndPosition() {
    val event = extractor.extract(controller("视频标题", PLAYING, position = 42_000))
    assertEquals("视频标题", event.title)
    assertEquals("PLAYING", event.playbackState)
    assertEquals(42_000, event.positionMs)
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*MediaMetadataExtractorTest'`

Expected: missing media collector APIs.

- [ ] **Step 3: Implement active-session callbacks**

Use the enabled notification-listener component with `MediaSessionManager`. Record initial active sessions, state changes and session disappearance. Do not record audio samples or screen content.

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest`

Expected: all app unit tests pass.

Commit: `feat: capture media session timelines`

### Task 4: Visible hostname observations

**Files:**
- Create: `app/src/main/cpp/dns_message_parser.h`
- Create: `app/src/main/cpp/dns_message_parser.cpp`
- Create: `app/src/main/cpp/dns_message_parser_test.cpp`
- Create: `app/src/main/cpp/tls_client_hello_parser.h`
- Create: `app/src/main/cpp/tls_client_hello_parser.cpp`
- Create: `app/src/main/cpp/tls_client_hello_parser_test.cpp`
- Modify: `app/src/main/cpp/CMakeLists.txt`
- Modify: `app/src/main/cpp/forwarder_engine.cpp`
- Modify: `app/src/main/kotlin/com/redmiklab/app/NativeConnectionObserver.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/NativeForwarderContract.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/EndpointObservationCache.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/EndpointObservationCacheTest.kt`

**Interfaces:**
- Produces native callback `onEndpointObserved(ip, hostname, source, observedAtEpochMs)`.
- Produces cache lookup `hostnameFor(ip, atEpochMs): HostnameObservation?`.

- [ ] **Step 1: Add failing native parser tests**

Tests use fixed byte arrays for a DNS A response and a TLS ClientHello containing `video.example.com`; malformed and truncated input must return no observation.

- [ ] **Step 2: Compile the standalone parser tests and verify RED**

Run: `c++ -std=c++17 app/src/main/cpp/dns_message_parser.cpp app/src/main/cpp/dns_message_parser_test.cpp -o /tmp/redmiklab-dns-parser-test`

Expected: compilation failure until parser sources and symbols exist. Repeat with the TLS parser test.

- [ ] **Step 3: Implement metadata-only parsers and callback**

Parse DNS names and A/AAAA answers without retaining payload. Parse only plaintext TLS ClientHello SNI. Do not implement QUIC decryption, ECH or HTTPS content parsing.

- [ ] **Step 4: Run standalone native parser tests, Android native build and Kotlin cache tests**

Run the two `/tmp/redmiklab-*-parser-test` binaries, then run `./gradlew :app:externalNativeBuildDebug :app:testDebugUnitTest`.

Expected: parser and cache tests pass.

Commit: `feat: observe DNS and TLS hostnames`

### Task 5: Endpoint and behavior classification

**Files:**
- Create: `core/model/src/main/kotlin/com/redmiklab/model/EndpointClassifier.kt`
- Create: `core/model/src/main/kotlin/com/redmiklab/model/BehaviorEvidenceClassifier.kt`
- Test: `core/model/src/test/kotlin/com/redmiklab/model/EndpointClassifierTest.kt`
- Test: `core/model/src/test/kotlin/com/redmiklab/model/BehaviorEvidenceClassifierTest.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ConnectionCaptureVpnService.kt`

**Interfaces:**
- Produces: endpoint categories `VIDEO_CDN`, `ADVERTISING`, `ANALYTICS`, `API`, `PUSH`, `OTHER`, `UNKNOWN`.
- Produces: behavior result containing classification, confidence and reason codes.

- [ ] **Step 1: Write failing classification tests**

```kotlin
@Test fun lockedPlayingVideoCdnIsLikelyBackgroundPlayback() {
    val result = classifier.classify(evidence(screenLocked = true, mediaPlaying = true, endpoint = VIDEO_CDN))
    assertEquals(LIKELY_BACKGROUND_PLAYBACK, result.classification)
    assertEquals(HIGH, result.confidence)
}

@Test fun pausedVideoCdnBurstIsPossiblePreload() {
    val result = classifier.classify(evidence(mediaPlaying = false, endpoint = VIDEO_CDN, burstBytes = 8_000_000))
    assertEquals(POSSIBLE_PRELOAD, result.classification)
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :core:model:testDebugUnitTest`

Expected: missing classifier APIs.

- [ ] **Step 3: Implement explainable rules**

Every result contains stable reason codes. Missing media permission caps confidence and cannot emit media-dependent high-confidence playback classifications.

- [ ] **Step 4: Run model and app tests**

Run: `./gradlew :core:model:testDebugUnitTest :app:testDebugUnitTest`

Expected: all selected tests pass.

Commit: `feat: classify endpoints and playback behavior`

### Task 6: Report schema and compatibility

**Files:**
- Modify: `feature/reports/src/main/kotlin/com/redmiklab/reports/RoomReportExporter.kt`
- Modify: `feature/reports/src/main/kotlin/com/redmiklab/reports/HtmlReportRenderer.kt`
- Modify: `feature/reports/src/main/kotlin/com/redmiklab/reports/ReportZipExporter.kt`
- Test: `feature/reports/src/test/kotlin/com/redmiklab/reports/ReportZipExporterTest.kt`
- Test: `feature/reports/src/test/kotlin/com/redmiklab/reports/HtmlReportRendererTest.kt`

**Interfaces:**
- Produces schemaVersion 2 JSON and five new CSV files.
- Preserves old ZIP import behavior.

- [ ] **Step 1: Write failing ZIP-content tests**

```kotlin
@Test fun exportsMediaNotificationsEndpointsAndClassifications() {
    val entries = exportEntries(databaseWithCorrelatedEvidence())
    assertTrue("media_events.csv" in entries)
    assertTrue(entries.getValue("notification_events.csv").contains("完整正文"))
    assertTrue(entries.getValue("classifications.csv").contains("LIKELY_BACKGROUND_PLAYBACK"))
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :feature:reports:testDebugUnitTest`

Expected: missing ZIP entries and schema 2 fields.

- [ ] **Step 3: Implement CSV/HTML/JSON output**

Escape every user-visible field. Include permission availability, planned/actual timing, media timeline, related notifications, hostname source, endpoint category, confidence and reason codes.

- [ ] **Step 4: Run report tests and commit**

Run: `./gradlew :feature:reports:testDebugUnitTest`

Expected: all report tests pass, including legacy import fixtures.

Commit: `feat: export correlated media and endpoint evidence`

### Task 7: Permission UI, full verification and K80 installation

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/MainActivity.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/MediaAccessState.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/MediaAccessStateTest.kt`
- Create: `docs/superpowers/progress/2026-07-16/media-endpoint-correlation.md`

- [ ] **Step 1: Write failing permission-state tests**

Test enabled, disabled and revoked-during-run states; each maps to explicit UI and report status.

- [ ] **Step 2: Implement the media-access entry and status**

Add “媒体活动访问” management, show whether notification access is enabled, and explain the fallback behavior. Keep connection capture optional except for strict mode.

- [ ] **Step 3: Run complete verification**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`

Expected: all tests pass, lint has zero errors and the APK builds.

- [ ] **Step 4: Install and verify on K80**

Run: `/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: `Success`.

Verify a short foreground playback, locked background playback, cached playback and no-media-permission run. Confirm normal networking and Clash conflict messaging.

- [ ] **Step 5: Document evidence and commit**

Commit: `test: verify media and endpoint correlation on K80`
