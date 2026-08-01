# 亮屏辅助移动网络重连实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不解锁、不保持常亮的前提下，用两次有条件亮屏辅助最多五次移动网络重连，并把实际动作写入报告。

**Architecture:** 保持 `NetworkGuardianPolicy` 负责纯状态机和退避序列，新建纯 Kotlin `ReconnectAttemptCoordinator` 负责“等待、亮屏、再等待、实际重连”的顺序；Android `ScreenWakeRequester` 只负责发送内部透明 Activity 的 `PendingIntent`。服务在每个动作发生时写入现有诊断事件表，从而避免数据库升级风险。

**Tech Stack:** Kotlin、Android AlarmManager/PendingIntent、Room 现有 diagnostic_events、JUnit、Robolectric、Gradle Android Plugin。

## Global Constraints

- 同一 APK 支持 K60 与 K80。
- 一次网络丢失最多重试 5 次；第 1、4 次亮屏；亮屏后等待 10 秒；首次确认等待 30 秒。
- 不解锁、不保持常亮、不主动熄屏。
- 先写失败测试，完成后运行 `test lintDebug assembleDebug`。

---

### Task 1: 重连时序策略

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/ReconnectAttemptCoordinator.kt`
- Create: `app/src/test/kotlin/com/redmiklab/app/ReconnectAttemptCoordinatorTest.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/NetworkGuardianPolicy.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/NetworkGuardianPolicyTest.kt`

**Interfaces:**
- Produces: `ReconnectAttemptPolicy.shouldWakeScreen(attempt: Int): Boolean`、`screenSettleDelayMs`、`ReconnectAttemptCoordinator.schedule(...)`。
- Consumes: `NetworkGuardianPolicy` 传出的 delay、generation 和 attempt。

- [ ] **Step 1: 写失败测试**：断言退避为 `30s,30s,60s,120s,300s`，第 1/4 次先调用 wake、10 秒后才调用 reconnect，其他次数直接 reconnect，generation 过期时什么也不做。
- [ ] **Step 2: 验证 RED**：运行 `./gradlew :app:testDebugUnitTest --tests '*NetworkGuardianPolicyTest' --tests '*ReconnectAttemptCoordinatorTest'`，预期因新策略/API 尚不存在而失败。
- [ ] **Step 3: 最小实现**：实现纯策略和协调器；协调器依赖 `RetryScheduler.schedule(delayMs, task)`、`ScreenWakePort.request(...)`、`ReconnectPort.request(...)`、`ReconnectActionRecorder.record(...)`。
- [ ] **Step 4: 验证 GREEN**：重跑目标测试并确认通过。
- [ ] **Step 5: 提交**：`git commit -m "feat: coordinate screen-assisted reconnect attempts"`。

### Task 2: Android 安全亮屏与服务集成

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/ScreenWakeActivity.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/ScreenWakeRequester.kt`
- Create: `app/src/test/kotlin/com/redmiklab/app/ScreenWakeActivityTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ConnectionCaptureVpnService.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/GuardianRuntimeStore.kt`

**Interfaces:**
- Consumes: Task 1 的协调器端口。
- Produces: `AndroidScreenWakeRequester.request(generation, attempt): ScreenWakeRequestResult`。

- [ ] **Step 1: 写失败测试**：Robolectric 验证 Activity 创建后启用 show-when-locked/turn-screen-on，且不会关闭 Keyguard；服务协调测试验证亮屏失败仍会继续重连。
- [ ] **Step 2: 验证 RED**：运行新增测试，预期类不存在或行为不匹配。
- [ ] **Step 3: 最小实现**：注册透明内部 Activity；使用唯一 `PendingIntent` 和允许空闲执行的精确闹钟发送；服务接入协调器，并在 guard 校验通过后调用物理网络请求。
- [ ] **Step 4: 记录动作**：诊断运行期间把 `NETWORK_RETRY_SCHEDULED`、`SCREEN_WAKE_REQUESTED`、`NETWORK_RETRY_STARTED`、`NETWORK_RETRIES_EXHAUSTED` 写入 `diagnostic_events`；运行时存储最近动作。
- [ ] **Step 5: 验证 GREEN**：运行 App 单元测试并确认通过。
- [ ] **Step 6: 提交**：`git commit -m "feat: wake lock screen before selected reconnects"`。

### Task 3: 报告、完整验证与真机部署

**Files:**
- Modify: `feature/reports/src/main/kotlin/com/redmiklab/reports/RoomReportExporter.kt`
- Modify: `feature/reports/src/test/kotlin/com/redmiklab/reports/RoomReportExporterTest.kt`
- Create: `docs/superpowers/progress/2026-07-21/screen-assisted-reconnect.md`

**Interfaces:**
- Consumes: `diagnostic_events` 中的重连和亮屏事件。
- Produces: HTML 发现说明与 `diagnostic_events.csv` 可验证时间线。

- [ ] **Step 1: 写失败测试**：报告测试插入亮屏/重连事件，断言 ZIP 的 HTML 说明存在且 CSV 保留事件和时间。
- [ ] **Step 2: 验证 RED**：运行 `:feature:reports:testDebugUnitTest`，确认新增断言失败。
- [ ] **Step 3: 最小实现**：增加亮屏辅助重连的报告发现与计数指标。
- [ ] **Step 4: 完整验证**：运行 `./gradlew test lintDebug assembleDebug`，要求退出码 0。
- [ ] **Step 5: 安全安装**：通过 ADB 查询设备型号和数据库活动 Run；仅在 `RUNNING/FINALIZING=0` 时执行 `adb install -r app/build/outputs/apk/debug/app-debug.apk`。
- [ ] **Step 6: 真机验证**：锁屏后触发内部亮屏路径，确认屏幕点亮但 Keyguard 仍锁定；检查 logcat 无崩溃，等待系统自然熄屏。
- [ ] **Step 7: 记录和提交**：写入构建、设备、安装和真机证据，提交 `git commit -m "docs: record screen-assisted reconnect rollout"`。
