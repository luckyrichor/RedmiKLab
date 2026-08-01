# Scheme A Android Visual Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android 主页面、设置弹窗和所有报告二级页面忠实还原为用户此前确认的方案 A 配色、板块强调和操作层级。

**Architecture:** 在 `AppUi` 中集中定义方案 A 颜色、板块强调类型和按钮视觉角色；`MainActivity` 只声明每个板块与按钮属于哪种角色。所有点击处理和业务调用保持原样。

**Tech Stack:** Kotlin、Android Views、Robolectric、Gradle、Android Lint

## Global Constraints

- 不修改诊断、计划、VPN、报告数据、数据库和权限逻辑。
- 使用方案 A 原始颜色：`#EDF2F7`、`#172131`、`#697689`、`#2864DC`、`#7854C4`、`#DB8722`、`#168A68`、`#59677A`。
- “设置输出文件名”摘要固定显示“自动同步”。
- 只有“立即诊断”使用实心蓝色。
- 文档存放在日期目录 `2026-07-23`。

---

### Task 1: 锁定视觉角色

**Files:**
- Modify: `app/src/test/kotlin/com/redmiklab/app/MainActivityLayoutTest.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/AppUi.kt`

**Interfaces:**
- Produces: `SectionTone`、`ActionTone`，以及设置在 View tag 上的可测试视觉角色。

- [ ] **Step 1: 写失败测试**

为主页面新增断言：

```kotlin
assertEquals(1, viewsWithTag(root, AppUi.ActionTone.PRIMARY).size)
assertEquals(1, viewsWithTag(root, AppUi.ActionTone.TONAL).size)
assertTrue(viewsWithTag(root, AppUi.SectionTone.MODE).isNotEmpty())
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.MainActivityLayoutTest
```

Expected: FAIL，因为 `SectionTone`、`ActionTone` 及对应 tag 尚不存在。

- [ ] **Step 3: 实现集中式视觉角色**

在 `AppUi.kt` 中增加：

```kotlin
enum class SectionTone { PARAMETER, MODE, PERMISSION, RUN, REPORT }
enum class ActionTone { PRIMARY, TONAL, NEUTRAL, DANGER }
```

让 `section` 绘制左侧短竖条，让 `addAction` 按角色选择方案 A 背景、文字和描边。

- [ ] **Step 4: 运行测试并确认通过**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.MainActivityLayoutTest
```

Expected: PASS。

### Task 2: 主页面应用方案 A

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/MainActivity.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/MainActivityLayoutTest.kt`

**Interfaces:**
- Consumes: `AppUi.SectionTone`、`AppUi.ActionTone`

- [ ] **Step 1: 写失败测试**

断言主页面：

```kotlin
assertEquals("自动同步", textForSetting(root, "设置输出文件名"))
assertEquals(4, viewsWithTag(root, AppUi.ActionTone.NEUTRAL).size)
```

并断言夜间计划不是 `PRIMARY`、报告区不存在 `PRIMARY`。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.MainActivityLayoutTest
```

Expected: FAIL，当前输出摘要仍展示文件名，夜间计划和历史导出仍标记为主按钮。

- [ ] **Step 3: 应用板块与按钮角色**

- 参数：`PARAMETER`
- 模式：`MODE`
- 权限：`PERMISSION`
- 立即运行、夜间计划：`RUN`
- 报告：`REPORT`
- 立即诊断：`PRIMARY`
- 停止：`DANGER`
- 安排夜间诊断：`TONAL`
- 取消计划和四个报告入口：`NEUTRAL`
- 输出文件名摘要：`自动同步`
- 立即运行、夜间计划各使用一行两列，报告入口使用两行两列；不改变任何点击处理。

- [ ] **Step 4: 运行测试并确认通过**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.MainActivityLayoutTest
```

Expected: PASS。

### Task 3: 设置弹窗和报告页面应用方案 A

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/AppUi.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticSettingsDialogs.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportActivitySupport.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportHistoryActivity.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportCompareActivity.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportAnalysisActivity.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportImportActivity.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/ReportUiPoliciesTest.kt`

**Interfaces:**
- Consumes: `AppUi.SectionTone`、`AppUi.ActionTone`、`AppUi.styleDialog`

- [ ] **Step 1: 写失败测试**

断言设置弹窗的正向按钮使用 `PRIMARY` 视觉角色，报告 Activity 根背景使用
`AppUi.BACKGROUND`，历史记录操作不因排列顺序自动成为主按钮。

- [ ] **Step 2: 运行测试并确认失败**

```bash
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.ReportActivitiesSmokeTest --tests com.redmiklab.app.ReportUiPoliciesTest
```

Expected: FAIL，因为弹窗和历史记录按钮尚未统一使用方案 A 角色。

- [ ] **Step 3: 应用统一视觉**

- 给全部设置弹窗应用统一圆角、字段描边、次要说明、取消和保存按钮样式。
- 给历史/最近/浏览等入口继续打开的选择、编辑和确认弹窗应用同一套样式。
- 历史记录卡片与四个操作采用白色细描边和同级按钮。
- 比较、导入和分析页面继续复用 `AppUi.page`、`section`、`addAction`。
- 分析图表色改为方案 A 蓝、紫、橙、绿序列。

- [ ] **Step 4: 运行测试并确认通过**

```bash
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.ReportActivitiesSmokeTest --tests com.redmiklab.app.ReportUiPoliciesTest
```

Expected: PASS。

### Task 4: 全量验证与真机同步

**Files:**
- Modify: `docs/superpowers/progress/2026-07-23/scheme-a-android-visual-alignment.md`

- [ ] **Step 1: 运行完整验证**

```bash
./gradlew test lintDebug assembleDebug
git diff --check
```

Expected: `BUILD SUCCESSFUL`，且 `git diff --check` 无输出。

- [ ] **Step 2: 提交**

```bash
git add app/src/main app/src/test docs/superpowers
git commit -m "style: restore scheme a visual hierarchy"
```

- [ ] **Step 3: 安装前检查并覆盖安装**

使用 ADB 检查不存在 `RUNNING/FINALIZING`，然后：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`。

- [ ] **Step 4: 真机验证**

启动应用，确认进程存在、无 `FATAL EXCEPTION`，数据库版本和历史记录数量保持不变，并核对手机上的 APK 哈希与本地 APK 相同。
