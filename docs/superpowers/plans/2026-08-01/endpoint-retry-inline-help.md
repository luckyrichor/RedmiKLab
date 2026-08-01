# 测速重试内联说明卡片实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将测速地址弹窗的失败重试说明改为紧邻设置行、不会遮挡其他内容的可展开内联卡片。

**Architecture:** `AppUi` 提供与方案 A 一致的通用内联说明卡片，负责显示、隐藏和关闭；`DiagnosticSettingsDialogs.endpoints` 将现有问号点击事件改接到该卡片，并把卡片插入失败重试行之后。其他问号继续使用现有 `PopupWindow`。

**Tech Stack:** Kotlin、Android Views、Robolectric、JUnit 4、Gradle。

## Global Constraints

- 只修改“设置测速地址”弹窗中的“失败重试间隔”说明交互。
- 展开和收起不能修改或保存任何测速配置。
- 继续使用方案 A 的颜色、细边框、圆角和紧凑字号。
- 使用 JDK 17、`ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`。

---

### Task 1: 实现并接入内联说明卡片

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/AppUi.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticSettingsDialogs.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt`

**Interfaces:**
- Produces: `AppUi.InlineHelpCard.view: LinearLayout`
- Produces: `AppUi.InlineHelpCard.toggle()`、`show()`、`hide()`
- Produces: 标签 `endpoint_retry_help_icon` 和 `scheme_a_inline_help_card`，用于真实界面测试定位。

- [ ] **Step 1: 写失败测试**

打开测速地址弹窗，取得问号和内联卡片。断言卡片初始为 `GONE`；点击问号后为 `VISIBLE`，且在弹窗视图遍历顺序中位于失败重试行之后；点击卡片的 `scheme_a_inline_help_close` 后恢复为 `GONE`，并确认没有生成新的 `AlertDialog`。

```kotlin
val icon = views.first { it.tag == "endpoint_retry_help_icon" }
val card = views.first { it.tag == "scheme_a_inline_help_card" }
assertEquals(View.GONE, card.visibility)
icon.performClick()
assertEquals(View.VISIBLE, card.visibility)
views.first { it.tag == "scheme_a_inline_help_close" }.performClick()
assertEquals(View.GONE, card.visibility)
```

- [ ] **Step 2: 运行测试确认 RED**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.ReportActivitiesSmokeTest
```
Expected: FAIL，因为旧弹窗不存在内联说明卡片，问号仍打开悬浮窗口。

- [ ] **Step 3: 实现方案 A 内联卡片**

在 `AppUi` 中新增 `InlineHelpCard`，卡片使用 `TONAL_SURFACE` 背景、`ACTION_BORDER` 细边框、10dp 圆角、12sp 正文和右上角关闭符号；初始 `GONE`。`show`、`hide`、`toggle` 只改变可见性。

- [ ] **Step 4: 接入测速地址弹窗**

在 `DiagnosticSettingsDialogs.endpoints` 中创建内联卡片，将失败重试问号的点击事件覆盖为 `toggle()`，依次向表单加入失败重试行和卡片，保证展开时按钮自然下移。

- [ ] **Step 5: 运行测试确认 GREEN**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.ReportActivitiesSmokeTest
```
Expected: PASS。

- [ ] **Step 6: 完整验证与提交**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew test lintDebug assembleDebug
git diff --check
```
Expected: `BUILD SUCCESSFUL` 且 `git diff --check` 无输出。随后提交：

```bash
git add app/src/main/kotlin/com/redmiklab/app/AppUi.kt app/src/main/kotlin/com/redmiklab/app/DiagnosticSettingsDialogs.kt app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt
git commit -m "fix: show retry help inline"
```

### Task 2: K60 真机验收

**Files:**
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: Task 1 生成的内联说明交互。

- [ ] **Step 1: 安装前确认无活动诊断**

只读提取数据库与 WAL，确认不存在 `RUNNING` 或 `FINALIZING` 记录。

- [ ] **Step 2: 安装并截图验收**

覆盖安装 APK，打开“设置测速地址”，点击失败重试问号；确认卡片紧接设置行显示，主、备用地址、数值、单位、取消和保存按钮均未被遮挡。

- [ ] **Step 3: 检查日志与工作区**

确认 logcat 不包含 `FATAL EXCEPTION`，`git status --short` 为空。
