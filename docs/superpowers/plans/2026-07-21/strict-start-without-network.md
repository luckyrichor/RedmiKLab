# 严格模式断网启动修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 严格模式在计划开始前已经失去物理移动网络或 VPN 隧道时，仍创建诊断 Run、记录网络缺口并继续严格调度与网络恢复。

**Architecture:** 用纯 Kotlin 启动策略区分永久配置错误与暂时隧道缺失。`NightDiagnosticService` 只对配置错误拒绝启动；暂时缺失隧道时先持久化 Run，再挂接网络守护与严格协调器，并记录可审计事件。

**Tech Stack:** Kotlin、Android Service、Room、JUnit、Robolectric、Gradle。

## Global Constraints

- 同一 APK 继续兼容 Redmi K60 与 K80。
- 严格模式仍要求用户已启用连接级采集，但不要求计划开始瞬间 VPN 隧道已经建立。
- 没有物理网络时，快照和主动测速计划仍继续，隧道由网络守护器恢复。
- 生产逻辑必须先有失败测试，再做最小修复。

---

### Task 1: 修复严格模式断网启动判定

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/DiagnosticRunStartPolicy.kt`
- Create: `app/src/test/kotlin/com/redmiklab/app/DiagnosticRunStartPolicyTest.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/NightDiagnosticService.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/MainActivity.kt`

**Interfaces:**
- Consumes: `DiagnosticConfig.validate()`、`DiagnosticConfig.validateRuntime(connectionCaptureEnabled)`、连接级采集瞬时运行状态。
- Produces: `DiagnosticRunStartDecision`，取值为 `REJECT`、`START_READY` 或 `START_WITH_TUNNEL_GAP`。

- [x] **Step 1: 写失败测试**

覆盖严格模式连接级采集已启用但隧道暂时停止时返回 `START_WITH_TUNNEL_GAP`，以及连接级采集未启用时返回 `REJECT`。

- [x] **Step 2: 运行定向测试并确认因缺少策略实现而失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.DiagnosticRunStartPolicyTest`

Expected: FAIL，原因是启动策略尚不存在。

- [x] **Step 3: 实现最小策略并接入服务**

服务和页面启动入口统一使用同一策略，不再把 `connectionCaptureRunning == false` 当成永久配置错误；遇到 `START_WITH_TUNNEL_GAP` 时先创建 Run，然后写入 `RUN_STARTED_WITH_TUNNEL_GAP`，继续挂接守护器、初始快照和严格协调器。

- [x] **Step 4: 运行定向测试并确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.DiagnosticRunStartPolicyTest`

Expected: PASS。

### Task 2: 完整验证与真机同步

**Files:**
- Create: `docs/superpowers/progress/2026-07-21/strict-start-without-network.md`

- [x] **Step 1: 运行完整单元测试、Lint 和 Debug APK 构建**

Run: `./gradlew test lintDebug assembleDebug`

Expected: BUILD SUCCESSFUL。

- [x] **Step 2: 提交 Git**

提交测试、实现、计划和进度记录。

- [x] **Step 3: 安装前检查手机与诊断状态**

确认当前连接设备型号，并查询 `diagnostic_runs` 中是否存在 `RUNNING` 或 `FINALIZING`；存在时不得覆盖安装。

- [x] **Step 4: 保留数据覆盖安装并验证**

使用 `adb install -r` 安装 Debug APK；验证 Activity 可启动、无 crash buffer、数据库版本与旧数据仍保留。
