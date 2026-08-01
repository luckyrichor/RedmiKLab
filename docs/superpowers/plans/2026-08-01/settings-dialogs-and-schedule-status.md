# 设置弹窗与夜间计划状态实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复设置弹窗层级和控件样式，将时间手输拆为小时与分钟，并在主页面持续显示准确的夜间计划状态及下次开始时间。

**Architecture:** 在 `AppUi` 中新增可复用的方案 A 单层下拉字段，并让帮助浮层使用锚点所属窗口；`DiagnosticSettingsDialogs` 只负责组合和校验设置控件。夜间计划由纯函数计算下次触发时间，`DiagnosticScheduler` 保存真正提交给系统闹钟的时间，`MainActivity` 读取持久化状态进行展示。

**Tech Stack:** Kotlin、Android Views、PopupWindow、AlarmManager、SharedPreferences、Robolectric、JUnit 4、Gradle。

## Global Constraints

- 不改变诊断、测速、连接级采集和夜间调度的执行规则。
- 下拉格式仅支持 `.zip` 与 `.json`；重试单位仅支持“秒、分、时”。
- 重试间隔换算后继续限制为 0–300 秒；时间继续使用 24 小时制且诊断窗口最长为 6 小时。
- 文档放在日期目录 `docs/superpowers/{specs,plans}/2026-08-01/`。
- 使用 JDK 17、`ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`。

---

### Task 1: 修复帮助浮层层级并实现单层下拉字段

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/AppUi.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticSettingsDialogs.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt`

**Interfaces:**
- Produces: `AppUi.choiceField(activity, choices, selectedIndex): ChoiceField`
- Produces: `ChoiceField.view: View`、`ChoiceField.selectedIndex: Int`
- Changes: `AppUi.showHelp(anchor, title, message)` 使用 `anchor.rootView` 的窗口令牌。

- [ ] **Step 1: 写失败测试**

在 `ReportActivitiesSmokeTest` 中断言测速地址和输出文件名弹窗不再包含 `Spinner`，各包含一个带 `scheme_a_choice_field` 标签的字段；字段包含当前值和 `▾`，点击后显示选择菜单。另创建对话框内问号并断言帮助浮层的锚点根视图属于该对话框窗口。

```kotlin
assertTrue(allViews(endpointDialog.window!!.decorView as ViewGroup).none { it is Spinner })
assertEquals(1, allViews(endpointDialog.window!!.decorView as ViewGroup).count { it.tag == "scheme_a_choice_field" })
assertTrue(collectText(endpointDialog.window!!.decorView as ViewGroup).contains("▾"))
```

- [ ] **Step 2: 运行测试确认 RED**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.ReportActivitiesSmokeTest
```
Expected: FAIL，因为弹窗仍包含 `Spinner`，且帮助浮层仍使用 Activity 根窗口。

- [ ] **Step 3: 实现最小单层下拉字段与窗口修复**

在 `AppUi.kt` 中新增 `ChoiceField`：单层圆角描边容器内放当前值和 `▾`，点击后用锚定 `PopupWindow` 显示选项；选择后更新索引和文字。修改 `showHelp`，以 `anchor.rootView` 调用 `showAtLocation`，确保弹窗内的问号使用弹窗窗口令牌。在 `DiagnosticSettingsDialogs` 中用该字段替换重试单位和报告格式的 `Spinner`。

- [ ] **Step 4: 运行测试确认 GREEN**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.ReportActivitiesSmokeTest
```
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/redmiklab/app/AppUi.kt app/src/main/kotlin/com/redmiklab/app/DiagnosticSettingsDialogs.kt app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt
git commit -m "fix: rebuild dialog dropdown controls"
```

### Task 2: 将时间手输改为小时与分钟双输入框

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticSettingsDialogs.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt`

**Interfaces:**
- Changes: `DiagnosticSettingsDialogs.time(...)` 保持现有调用签名。
- Produces: 两个带 `scheme_a_time_hour`、`scheme_a_time_minute` 标签的 `EditText`。

- [ ] **Step 1: 写失败测试**

打开时间弹窗，断言小时和分钟两个输入框均存在且默认分别为 `01`、`00`，页面不再出现“手动输入（HH:mm）”；分别输入 `12` 与 `34` 后点击保存，回调收到 `LocalTime.of(12, 34)`。

```kotlin
assertEquals("01", viewWithTag<EditText>(dialog, "scheme_a_time_hour").text.toString())
assertEquals("00", viewWithTag<EditText>(dialog, "scheme_a_time_minute").text.toString())
```

- [ ] **Step 2: 运行测试确认 RED**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.ReportActivitiesSmokeTest
```
Expected: FAIL，因为旧实现只有一个 `HH:mm` 输入框。

- [ ] **Step 3: 实现双输入框和同步校验**

用水平容器放小时输入框、只读冒号和分钟输入框；滚轮变化时分别刷新两框。保存时分别解析整数并校验小时 `0..23`、分钟 `0..59`，错误附着到对应输入框，合法时构造 `LocalTime.of(hour, minute)`。

- [ ] **Step 4: 运行测试确认 GREEN**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.ReportActivitiesSmokeTest
```
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/redmiklab/app/DiagnosticSettingsDialogs.kt app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt
git commit -m "fix: split manual time entry fields"
```

### Task 3: 保存准确的下次触发时间并展示夜间计划状态

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/NightScheduleUiPolicy.kt`
- Create: `app/src/test/kotlin/com/redmiklab/app/NightScheduleUiPolicyTest.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticPreferences.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticScheduler.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/MainActivity.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/MainActivityLayoutTest.kt`

**Interfaces:**
- Produces: `NightScheduleUiPolicy.nextStart(config, now, zoneId): Long`
- Produces: `NightScheduleUiPolicy.status(enabled, nextStartEpochMs, zoneId): String`
- Produces: `DiagnosticPreferences.nightScheduleNextStartEpochMs(): Long?`
- Produces: `DiagnosticPreferences.saveNightSchedule(enabled: Boolean, nextStartEpochMs: Long?)`

- [ ] **Step 1: 写策略与主页面失败测试**

测试 `23:00` 安排 `01:00` 得到下一天 01:00；测试未安排文案为“夜间计划：尚未安排”，已安排文案包含准确日期时间。主页面测试断言顶部始终包含 `night_schedule_status` 视图。

```kotlin
assertEquals("夜间计划：尚未安排", policy.status(false, null, zone))
assertEquals("夜间计划：已安排 · 下次开始 2026-08-02 01:00", policy.status(true, expectedEpoch, zone))
```

- [ ] **Step 2: 运行测试确认 RED**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.NightScheduleUiPolicyTest --tests com.redmiklab.app.MainActivityLayoutTest
```
Expected: FAIL，因为策略、持久化时间和状态视图尚不存在。

- [ ] **Step 3: 实现计划策略、持久化和调度写入**

创建纯策略计算下一次未来开始时刻与展示文案；`DiagnosticScheduler.schedule` 使用该策略计算 `triggerAt`，提交闹钟后调用 `saveNightSchedule(true, triggerAt)`；`cancel` 调用 `saveNightSchedule(false, null)`。读取旧版本只有布尔标志时，通过当前配置补算并保存一次迁移值。

- [ ] **Step 4: 在主页面加入状态视图和即时刷新**

在顶部摘要区域增加带 `night_schedule_status` 标签的 `TextView`。`refreshAll` 根据持久化状态渲染；安排和取消按钮完成后调用 `refreshAll()`，确保当前页面立即变化。

- [ ] **Step 5: 运行测试确认 GREEN**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.NightScheduleUiPolicyTest --tests com.redmiklab.app.MainActivityLayoutTest
```
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/kotlin/com/redmiklab/app/NightScheduleUiPolicy.kt app/src/main/kotlin/com/redmiklab/app/DiagnosticPreferences.kt app/src/main/kotlin/com/redmiklab/app/DiagnosticScheduler.kt app/src/main/kotlin/com/redmiklab/app/MainActivity.kt app/src/test/kotlin/com/redmiklab/app/NightScheduleUiPolicyTest.kt app/src/test/kotlin/com/redmiklab/app/MainActivityLayoutTest.kt
git commit -m "feat: display persisted night schedule status"
```

### Task 4: 完整验证与手机验收

**Files:**
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: Tasks 1–3 的全部界面和持久化行为。

- [ ] **Step 1: 运行完整验证**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew test lintDebug assembleDebug
```
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 2: 安装前检查当前诊断状态**

只读提取 `redmi_klab.db` 与 WAL，确认没有 `RUNNING` 或 `FINALIZING` 记录；若有，等待正常结束，不强行安装。

- [ ] **Step 3: 安装并执行真机界面验收**

覆盖安装 `app-debug.apk`，启动后核对测速地址问号浮层、单位下拉箭头、双框时间输入、报告格式下拉箭头，以及顶部“尚未安排”状态。

- [ ] **Step 4: 验收安排与取消状态**

安排一次夜间计划，确认顶部立即显示准确的下次开始时间；随后取消该验收计划，确认恢复“尚未安排”，避免留下非用户预期计划。

- [ ] **Step 5: 检查日志和 Git 状态**

确认 logcat 无 `FATAL EXCEPTION`，`git diff --check` 通过且 `git status --short` 为空。
