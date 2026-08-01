# Main Screen Layout Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复主页面夜间计划按钮颜色、板块标题对齐和状态栏重叠。

**Architecture:** 保留当前程序化 View 架构和业务回调，仅调整 `AppUi.section` 的标题行布局、`MainActivity` 的动作色调与 WindowInsets 分发。通过 Robolectric 验证结构与 inset 行为，再以 K60 真机截图验收。

**Tech Stack:** Kotlin、Android View、WindowInsets、Robolectric、Gradle。

## Global Constraints

- 不修改诊断、VPN、调度、报告及权限业务逻辑。
- 使用方案 A 现有色彩与自绘按钮组件。
- 同一 APK 继续支持 K60 与 K80。

---

### Task 1: 添加主页面视觉回归测试

**Files:**
- Modify: `app/src/test/kotlin/com/redmiklab/app/MainActivityLayoutTest.kt`

**Interfaces:**
- Consumes: `MainActivity`、`AppUi.section`、`AppUi.ActionTone`
- Produces: 三项布局行为的回归断言

- [ ] **Step 1: 写入失败测试**

断言“安排夜间诊断”为 `NEUTRAL`；断言标题行带 `scheme_a_section_header` 标签且竖线、标题垂直居中；向根视图分发顶部 inset 后断言标题区域顶部 padding 增加。

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.MainActivityLayoutTest`

Expected: 旧实现分别因 `TONAL`、文字单独 bottomMargin、顶部 inset 未应用而失败。

### Task 2: 最小化修复主页面布局

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/AppUi.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/MainActivity.kt`

**Interfaces:**
- Consumes: `WindowInsets.Type.statusBars()`、`AppUi.ActionTone.NEUTRAL`
- Produces: 对齐的板块标题行和状态栏安全标题区域

- [ ] **Step 1: 修改标题行和按钮色调**

为 section 标题容器设置标签、固定最小高度和整行底部间距；移除标题文字自身的 `bottomMargin`；将“安排夜间诊断”改为 `NEUTRAL`。

- [ ] **Step 2: 应用状态栏 inset**

保留标题区域基础 padding，并在 `setOnApplyWindowInsetsListener` 中将 `statusBars().top` 加到顶部；导航栏底部逻辑保持不变。

- [ ] **Step 3: 运行定向测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.MainActivityLayoutTest`

Expected: PASS。

### Task 3: 完整验证与 K60 真机验收

**Files:**
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: 当前 K60 USB 连接与设备数据库
- Produces: 已安装并截图验证的 APK

- [ ] **Step 1: 运行完整验证**

Run: `./gradlew test lintDebug assembleDebug`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 安装前检查诊断状态**

只在 `diagnostic_runs` 中不存在 `RUNNING`/`FINALIZING` 时执行覆盖安装。

- [ ] **Step 3: 安装并截图复核**

安装 APK，启动主页面，分别截取顶部与下方操作区，确认三项问题消失。

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/kotlin/com/redmiklab/app/AppUi.kt \
  app/src/main/kotlin/com/redmiklab/app/MainActivity.kt \
  app/src/test/kotlin/com/redmiklab/app/MainActivityLayoutTest.kt \
  docs/superpowers/specs/2026-08-01/main-screen-layout-fixes.md \
  docs/superpowers/plans/2026-08-01/main-screen-layout-fixes.md
git commit -m "fix: align main screen controls with system bars"
```

