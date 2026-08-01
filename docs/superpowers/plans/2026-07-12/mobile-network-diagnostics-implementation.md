# 移动网络夜间诊断（第一期）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建可安装于 Redmi K60 和 K80 的 Android App，在可配置夜间窗口内采集中国电信移动网络状态、主动探测和时段级流量证据，并导出可离线在电脑查看的报告。

**Architecture:** 单一 Kotlin/Jetpack Compose Android App 以接口隔离 Android 系统采集器；Room 保存标准化诊断运行、快照、探测和流量记录；前台服务编排夜间运行。纯 Kotlin 的规则和报告转换放在 `core:model`，以 JVM 单元测试覆盖；Android 适配由 `feature:diagnostics` 负责。

**Tech Stack:** Kotlin 2.0+、JDK 17、AGP 9.1.1、Gradle 9.3.1、compileSdk 37、minSdk 26、Jetpack Compose、AndroidX Lifecycle、Room、kotlinx.serialization、JUnit 4/Robolectric（仅 Android 适配层）。

## Global Constraints

- 目标设备为 Redmi K60/K80；同一 APK，不按机型分叉。
- 默认仅采集移动数据；不得在首期启动 `VpnService`、要求 Root 或要求 ADB 常驻。
- 用户可设置跨午夜窗口、采样/测速间隔和流量预算；默认 00:00–07:00、5/10/30 分钟和 100 MB/夜。
- 测速单次上限为 5 MB，预算耗尽后停止主动吞吐探测、继续低成本状态采集。
- 原始数据只保存在设备；ZIP 必须包括离线 `report.html`、CSV 和 JSON。
- 应用级流量结论仅限时段级关联；报告不得声称逐包或因果结论。
- 运行时用前台服务与可见通知；权限/省电限制/异常必须可见且可记录。

---

## File Structure

```text
settings.gradle.kts                         Gradle 模块声明
build.gradle.kts                            根插件声明
gradle/libs.versions.toml                   统一依赖版本
app/                                        Compose UI、Manifest、服务和装配
core/model/                                 纯 Kotlin 配置、记录、规则、导出模型
core/storage/                               Room 数据库、DAO、导出文件写入
feature/diagnostics/                        Android 网络/流量采集、探测、夜间编排
feature/reports/                            报告汇总、HTML/CSV/JSON 生成和导入对比
```

### Task 1: 建立可重复的 Android 构建骨架

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `README.md`, `.gitignore`
- Create: `app/build.gradle.kts`, `core/model/build.gradle.kts`, `core/storage/build.gradle.kts`, `feature/diagnostics/build.gradle.kts`, `feature/reports/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/redmiklab/app/MainActivity.kt`
- Test: `core/model/src/test/kotlin/com/redmiklab/model/BuildSanityTest.kt`

**Interfaces:**
- Produces: 5 个可解析 Gradle 模块；`./gradlew test` 与 `./gradlew :app:assembleDebug` 是全项目验证入口。

- [ ] **Step 1: 安装并验证 JDK/Android SDK 前置条件**

运行：

```bash
java -version
echo "$ANDROID_HOME"
ls "$ANDROID_HOME/platforms/android-37"
```

预期：JDK 17、Android SDK Platform 37.0、Android SDK Build Tools 36.0.0 与 Gradle 9.3.1 均可用。`compileSdk = 37` 对应 SDK Manager 包 `platforms;android-37.0`；Gradle 用于生成并固定项目的 Gradle Wrapper。若仍缺失，先安装这些组件，再继续，不能声称工程可构建。

- [ ] **Step 2: 写入会失败的构建健全性测试**

```kotlin
package com.redmiklab.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildSanityTest {
    @Test fun module_is_loaded() = assertEquals("redmi-klab", "redmi-klab")
}
```

- [ ] **Step 3: 运行测试，确认失败原因是模块尚不存在**

运行：`./gradlew :core:model:testDebugUnitTest`

预期：失败，提示找不到 Gradle wrapper 或 `:core:model` 项目。

- [ ] **Step 4: 建立最小 Gradle 多模块工程和 Compose 入口**

`settings.gradle.kts` 必须包含：

```kotlin
include(":app", ":core:model", ":core:storage", ":feature:diagnostics", ":feature:reports")
rootProject.name = "RedmiKLab"
```

各 Android 模块使用 `namespace = "com.redmiklab.<module>"`、`compileSdk = 37`、`minSdk = 26`、Java/Kotlin target 17。`MainActivity` 先仅渲染 `Text("RedmiKLab")`。

- [ ] **Step 5: 验证测试和 debug APK 构建**

运行：`./gradlew test :app:assembleDebug`

预期：退出码 0，生成 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 6: 提交骨架**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle README.md .gitignore app core feature
git commit -m "build: scaffold Android diagnostic app"
```

### Task 2: 定义配置、记录和诊断结论的纯 Kotlin 契约

**Files:**
- Create: `core/model/src/main/kotlin/com/redmiklab/model/DiagnosticConfig.kt`
- Create: `core/model/src/main/kotlin/com/redmiklab/model/DiagnosticRecord.kt`
- Create: `core/model/src/main/kotlin/com/redmiklab/model/DiagnosisRule.kt`
- Test: `core/model/src/test/kotlin/com/redmiklab/model/DiagnosticConfigTest.kt`
- Test: `core/model/src/test/kotlin/com/redmiklab/model/DiagnosisRuleTest.kt`

**Interfaces:**
- Produces: `DiagnosticConfig.validate(): List<ConfigIssue>`、`NetworkSnapshot`、`ProbeResult`、`TrafficWindow`、`DiagnosisRule.evaluate(records): List<Finding>`。

- [ ] **Step 1: 写入失败的配置校验测试**

```kotlin
@Test fun rejects_probe_limit_above_nightly_budget() {
    val config = DiagnosticConfig.default().copy(singleProbeLimitBytes = 101, nightlyBudgetBytes = 100)
    assertEquals(listOf(ConfigIssue.SingleProbeExceedsBudget), config.validate())
}

@Test fun accepts_window_that_crosses_midnight() {
    val config = DiagnosticConfig.default().copy(start = LocalTime.of(23, 0), end = LocalTime.of(6, 0))
    assertEquals(emptyList<ConfigIssue>(), config.validate())
}
```

- [ ] **Step 2: 运行失败测试**

运行：`./gradlew :core:model:testDebugUnitTest --tests '*DiagnosticConfigTest'`

预期：失败，`DiagnosticConfig` 未定义。

- [ ] **Step 3: 实现最小配置模型**

```kotlin
data class DiagnosticConfig(
    val start: LocalTime, val end: LocalTime,
    val snapshotMinutes: Int, val connectivityMinutes: Int, val throughputMinutes: Int,
    val singleProbeLimitBytes: Long, val nightlyBudgetBytes: Long,
) {
    fun validate(): List<ConfigIssue> = buildList {
        if (singleProbeLimitBytes > nightlyBudgetBytes) add(ConfigIssue.SingleProbeExceedsBudget)
        if (snapshotMinutes !in 1..60) add(ConfigIssue.InvalidSnapshotInterval)
    }
    companion object { fun default() = DiagnosticConfig(LocalTime.MIDNIGHT, LocalTime.of(7,0), 5, 10, 30, 5_000_000, 100_000_000) }
}
```

- [ ] **Step 4: 写入失败的双机同步低速规则测试并实现规则**

```kotlin
@Test fun marks_network_side_when_both_devices_are_slow_with_similar_radio() {
    val finding = DiagnosisRule.evaluate(twoSlow5gDeviceRecords()).single()
    assertEquals(FindingKind.PossibleNetworkSide, finding.kind)
}
```

实现 `DiagnosisRule`：仅当两个设备相同 30 分钟桶内吞吐低于各自基线 30%、均为 5G 且信号等级差不超过 1 时，返回 `PossibleNetworkSide`；文字为“倾向”，证据包含两个运行 ID。

- [ ] **Step 5: 验证模型测试**

运行：`./gradlew :core:model:testDebugUnitTest`

预期：全部通过。

- [ ] **Step 6: 提交模型契约**

```bash
git add core/model
git commit -m "feat: define diagnostic domain contracts"
```

### Task 3: 实现 Room 持久化和可查询的运行记录

**Files:**
- Create: `core/storage/src/main/kotlin/com/redmiklab/storage/DiagnosticDatabase.kt`
- Create: `core/storage/src/main/kotlin/com/redmiklab/storage/DiagnosticDao.kt`
- Create: `core/storage/src/main/kotlin/com/redmiklab/storage/Entities.kt`
- Create: `core/storage/src/test/kotlin/com/redmiklab/storage/DiagnosticDaoTest.kt`

**Interfaces:**
- Consumes: Task 2 的 `NetworkSnapshot`、`ProbeResult`、`TrafficWindow`。
- Produces: `DiagnosticRepository.startRun(config): RunId`、`append(record)`、`recordsFor(runId)`、`finishRun(runId, status)`。

- [ ] **Step 1: 写入失败的 DAO 往返测试**

```kotlin
@Test fun reads_snapshots_in_timestamp_order_for_one_run() = runTest {
    dao.insertSnapshot(snapshot(runId = "a", at = 20)); dao.insertSnapshot(snapshot(runId = "a", at = 10))
    assertEquals(listOf(10L, 20L), dao.snapshotsFor("a").map { it.timestampEpochMs })
}
```

- [ ] **Step 2: 运行失败测试**

运行：`./gradlew :core:storage:testDebugUnitTest --tests '*DiagnosticDaoTest'`

预期：失败，DAO/数据库尚未定义。

- [ ] **Step 3: 实现最小 Room schema 与 repository**

创建 `DiagnosticRunEntity`、`SnapshotEntity`、`ProbeEntity`、`TrafficEntity`，每个采集表均含 `runId` 与 `timestampEpochMs`；DAO 查询按 `timestampEpochMs ASC` 排序。使用 `@TypeConverter` 保存枚举，禁止将原始 IMSI、电话号码或内容流量写入数据库。

- [ ] **Step 4: 验证持久化测试**

运行：`./gradlew :core:storage:testDebugUnitTest`

预期：全部通过。

- [ ] **Step 5: 提交存储层**

```bash
git add core/storage
git commit -m "feat: persist diagnostic runs and records"
```

### Task 4: 实现 Android 网络快照和时段级流量采集器

**Files:**
- Create: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/NetworkSnapshotSource.kt`
- Create: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/AndroidNetworkSnapshotSource.kt`
- Create: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/TrafficStatsSource.kt`
- Create: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/AndroidTrafficStatsSource.kt`
- Create: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/PermissionState.kt`
- Test: `feature/diagnostics/src/test/kotlin/com/redmiklab/diagnostics/PermissionStateTest.kt`

**Interfaces:**
- Produces: `suspend fun NetworkSnapshotSource.read(): NetworkSnapshot` 和 `suspend fun TrafficStatsSource.readWindow(startMs: Long, endMs: Long): TrafficWindow`。

- [ ] **Step 1: 写入失败的权限状态测试**

```kotlin
@Test fun requires_usage_access_before_app_traffic_collection() {
    assertEquals(PermissionState.MissingUsageAccess, PermissionState.from(notification = true, phone = true, usageAccess = false))
}
```

- [ ] **Step 2: 运行失败测试**

运行：`./gradlew :feature:diagnostics:testDebugUnitTest --tests '*PermissionStateTest'`

预期：失败，`PermissionState` 未定义。

- [ ] **Step 3: 实现采集接口和 Android 适配**

`AndroidNetworkSnapshotSource` 使用 `ConnectivityManager` 判断移动数据、`TelephonyManager` 读取许可范围内的网络类型/信号；无法取得的字段使用明确的 `Unavailable`，不伪造数值。`AndroidTrafficStatsSource` 使用 `NetworkStatsManager` 和用户授予的 usage access 查询移动网络时间窗，记录统计窗口边界与 `isApproximate = true`。捕获 `SecurityException` 并转换为 `MissingUsageAccess` 事件。

- [ ] **Step 4: 在 Manifest 声明必要权限和设置入口**

声明 `ACCESS_NETWORK_STATE`、`INTERNET`、`FOREGROUND_SERVICE`、`POST_NOTIFICATIONS`、`READ_PHONE_STATE` 与 usage access intent；不得声明定位、联系人、电话、SMS 或 VPN 权限。设置页必须可跳转到 usage access 和电池优化设置。

- [ ] **Step 5: 验证模块与静态检查**

运行：`./gradlew :feature:diagnostics:testDebugUnitTest :app:lintDebug`

预期：退出码 0。

- [ ] **Step 6: 提交系统采集层**

```bash
git add feature/diagnostics app/src/main/AndroidManifest.xml
git commit -m "feat: collect mobile network and traffic snapshots"
```

### Task 5: 实现预算受控的网络探测与夜间前台服务

**Files:**
- Create: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/ProbeRunner.kt`
- Create: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/BudgetTracker.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/NightDiagnosticService.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/RunScheduler.kt`
- Test: `feature/diagnostics/src/test/kotlin/com/redmiklab/diagnostics/BudgetTrackerTest.kt`
- Test: `feature/diagnostics/src/test/kotlin/com/redmiklab/diagnostics/ProbeRunnerTest.kt`

**Interfaces:**
- Consumes: `DiagnosticConfig`、Task 3 repository、Task 4 sources。
- Produces: `ProbeRunner.run(config, budget): ProbeResult`、`BudgetTracker.canSpend(bytes): Boolean`、`NightDiagnosticService`。

- [ ] **Step 1: 写入失败的预算测试**

```kotlin
@Test fun refuses_probe_that_would_exceed_nightly_budget() {
    val budget = BudgetTracker(limitBytes = 100, usedBytes = 98)
    assertFalse(budget.reserve(5))
    assertEquals(98, budget.usedBytes)
}
```

- [ ] **Step 2: 运行失败测试**

运行：`./gradlew :feature:diagnostics:testDebugUnitTest --tests '*BudgetTrackerTest'`

预期：失败，`BudgetTracker` 未定义。

- [ ] **Step 3: 实现探测和预算**

`ProbeRunner` 先执行可配置 DNS/HTTPS 延迟探测，再只在 `BudgetTracker.reserve(singleProbeLimitBytes)` 成功时执行带 `Range` 限制的 HTTPS 下载；结果记录实际字节数、超时和失败类型。不得把测试目标硬编码为视频服务或私人 URL；初始默认使用可配置 HTTPS endpoint。

- [ ] **Step 4: 实现服务状态机并写入失败恢复测试**

```kotlin
@Test fun keeps_snapshot_collection_after_throughput_budget_is_exhausted() {
    val next = RunState.Running(budgetExhausted = true).next(Event.ThroughputTick)
    assertEquals(Action.SkipThroughputKeepSnapshot, next.action)
}
```

服务在窗口开始时由 `AlarmManager`/`WorkManager` 触发，立即调用 `startForeground()`；每轮写入快照并按间隔运行探测。结束、权限撤销、重启恢复、系统终止和网络失败都写入运行事件；网络失败采用最多三次指数退避。服务结束时调用 repository `finishRun`。

- [ ] **Step 5: 验证诊断测试**

运行：`./gradlew :feature:diagnostics:testDebugUnitTest`

预期：全部通过。

- [ ] **Step 6: 提交前台运行能力**

```bash
git add feature/diagnostics app/src/main
git commit -m "feat: run budgeted nightly diagnostics"
```

### Task 6: 生成可导入、可在电脑离线查看的报告包

**Files:**
- Create: `feature/reports/src/main/kotlin/com/redmiklab/reports/ReportGenerator.kt`
- Create: `feature/reports/src/main/kotlin/com/redmiklab/reports/HtmlReportRenderer.kt`
- Create: `feature/reports/src/main/kotlin/com/redmiklab/reports/ReportZipExporter.kt`
- Create: `feature/reports/src/main/kotlin/com/redmiklab/reports/ReportZipImporter.kt`
- Test: `feature/reports/src/test/kotlin/com/redmiklab/reports/ReportZipExporterTest.kt`
- Test: `feature/reports/src/test/kotlin/com/redmiklab/reports/HtmlReportRendererTest.kt`

**Interfaces:**
- Consumes: Task 2 records/findings and Task 3 repository queries。
- Produces: `ReportZipExporter.export(runId, output: OutputStream): ExportResult` 与 `ReportZipImporter.read(input): ImportedReport`。

- [ ] **Step 1: 写入失败的 ZIP 内容测试**

```kotlin
@Test fun exported_zip_contains_html_csv_and_json() {
    val names = exportMinimalRun().zipEntryNames()
    assertEquals(setOf("report.html", "snapshots.csv", "probes.csv", "traffic.csv", "report.json"), names)
}
```

- [ ] **Step 2: 运行失败测试**

运行：`./gradlew :feature:reports:testDebugUnitTest --tests '*ReportZipExporterTest'`

预期：失败，导出器尚未定义。

- [ ] **Step 3: 实现确定性导出与离线 HTML**

`ReportGenerator` 以运行记录生成结论和证据。`HtmlReportRenderer` 只输出内嵌 CSS/SVG/数据，不引用 CDN、远程脚本或网络资源；必须显示运行时间、设备别名、参数、缺失区间、结论证据和“关联不等于因果”提示。CSV 使用 UTF-8 BOM 与固定列顺序；JSON 使用 `schemaVersion = 1`。

- [ ] **Step 4: 写入并实现导入兼容性测试**

```kotlin
@Test fun rejects_unknown_report_schema_version() {
    assertFailsWith<UnsupportedSchemaException> { importer.read(zipWithSchema(99)) }
}
```

- [ ] **Step 5: 验证报告模块**

运行：`./gradlew :feature:reports:testDebugUnitTest`

预期：全部通过。

- [ ] **Step 6: 提交导出能力**

```bash
git add feature/reports
git commit -m "feat: export offline diagnostic reports"
```

### Task 7: 完成 Compose 界面、权限引导与真机验收清单

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/ui/HomeScreen.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/ui/ConfigScreen.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/ui/ReportScreen.kt`
- Create: `app/src/main/kotlin/com/redmiklab/app/ui/PermissionGuide.kt`
- Create: `docs/acceptance/k60-k80-night-run.md`
- Test: `app/src/test/kotlin/com/redmiklab/app/ConfigViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2 config、Task 5 service state、Task 6 export result。
- Produces: 可配置/可启动/可导出的用户界面与可复现真机验收流程。

- [ ] **Step 1: 写入失败的配置保存测试**

```kotlin
@Test fun save_is_disabled_when_the_budget_is_invalid() {
    val state = ConfigViewModel(initial = invalidBudgetConfig()).state.value
    assertFalse(state.canSave)
    assertEquals(ConfigIssue.SingleProbeExceedsBudget, state.issues.single())
}
```

- [ ] **Step 2: 运行失败测试**

运行：`./gradlew :app:testDebugUnitTest --tests '*ConfigViewModelTest'`

预期：失败，`ConfigViewModel` 未定义。

- [ ] **Step 3: 实现最小可用 UI**

首页显示下一窗口、运行状态、权限状态和启动/停止按钮。配置页提供开始/结束时间与 5 个采样/预算参数，保存前调用 `validate()`。报告页列出已完成运行、摘要、导出按钮和导入对方 ZIP 按钮。权限页解释“使用情况访问”“通知”“电话状态”“无限制电池”和“自启动”分别为何需要；不得误导用户授予不需要的权限。

- [ ] **Step 4: 编写双机真机验收步骤**

`docs/acceptance/k60-k80-night-run.md` 必须包含：在两机安装同一 debug APK、授予权限、设置电池/自启动、配置相同 30 分钟测试窗口、锁屏、检查通知、次日导出 ZIP 到电脑、断网/预算耗尽的预期记录、K60 导入 K80 ZIP 的验证步骤。

- [ ] **Step 5: 完整验证**

运行：

```bash
./gradlew test lint assembleDebug
git diff --check
git status --short
```

预期：Gradle 三项退出码 0；无空白错误；仅本任务的预期修改待提交。

- [ ] **Step 6: 提交 UI 和验收文档**

```bash
git add app docs/acceptance
git commit -m "feat: add diagnostic configuration and report UI"
```

## Plan Self-Review

- 覆盖性：Task 1 搭建；Task 2 规则/参数；Task 3 本地存储；Task 4 网络/流量系统采集；Task 5 夜间服务、预算和失败恢复；Task 6 电脑离线报告与双机导入；Task 7 UI、权限引导和 K60/K80 验收。
- 限制落实：任务 4 明确时段级 `isApproximate`；任务 5 限制探测速流量和前台服务；任务 6 禁用外部资源；全计划没有 VPN/Root/ADB 实现。
- 一致性：所有后续任务都以 Task 2 的数据模型和 Task 3 的 repository 为边界；导出固定为 `report.html`、三份 CSV、`report.json`。
- 无占位符：计划无未决技术或路径占位项；当前工作机缺 JDK/SDK 的环境缺口已作为 Task 1 的可验证前置条件明确列出。
