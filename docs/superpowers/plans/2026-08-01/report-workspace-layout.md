# Report Workspace Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将分析、导入和比较报告页面改造成视觉平衡的方案 A 报告工作台，并把历史记录选择窗口改成宽松、易识别的报告卡片列表。

**Architecture:** 新增一个只负责报告页面展示组件的 `ReportWorkspaceUi`，集中提供填满视口的滚动容器、空状态工作卡片、状态卡片和指标网格，避免继续扩大通用 `AppUi`。历史记录选择使用独立的 `ReportHistoryChoiceAdapter` 渲染结构化记录；现有 Activity 继续负责数据读取和业务回调，后端报告逻辑保持不变。

**Tech Stack:** Kotlin、Android Views、Robolectric、JUnit 4、Gradle、现有 `AppUi` 方案 A 设计令牌。

## Global Constraints

- 仅调整页面结构、样式和展示文案，不修改报告读取、历史记录查询、分析、比较、导入或诊断逻辑。
- 蓝灰背景必须完整覆盖状态栏与导航栏之间的可用视口。
- 数据来源按钮统一使用白底描边 `AppUi.ActionTone.NEUTRAL`。
- K60 与 K80 使用同一套自适应布局，不写死设备像素坐标。
- 继续使用现有 `WindowInsets` 安全区处理。
- 所有生产代码遵循测试先行，独立变更分别提交 Git。

---

### Task 1: Shared Report Workspace Components

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/ReportWorkspaceUi.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt`

**Interfaces:**
- Consumes: `AppUi.page`, `AppUi.actionButton`, public `AppUi` color constants and `Activity.dp`.
- Produces: `ReportWorkspaceUi.scroll(activity, page): ScrollView`, `ReportWorkspaceUi.emptyState(...) : LinearLayout`, `ReportWorkspaceUi.stateCard(...) : LinearLayout`, and `ReportWorkspaceUi.metricGrid(...) : LinearLayout`.

- [ ] **Step 1: Write failing component behavior tests**

Add tests that instantiate a report page through `ReportWorkspaceUi.scroll` and assert:

```kotlin
val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
val page = AppUi.page(activity, "测试页面")
val scroll = ReportWorkspaceUi.scroll(activity, page)

assertTrue(scroll.isFillViewport)
assertEquals(AppUi.BACKGROUND, (page.background as ColorDrawable).color)
```

Add an empty-state test asserting the card tag is `report_workspace_empty_state`, it contains a title, format badges, and all action buttons retain the supplied `NEUTRAL` tone.

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.ReportActivitiesSmokeTest --console=plain
```

Expected: compilation fails because `ReportWorkspaceUi` does not exist.

- [ ] **Step 3: Implement the shared presentation components**

Create `ReportWorkspaceUi` with:

```kotlin
object ReportWorkspaceUi {
    fun scroll(activity: Activity, page: LinearLayout): ScrollView

    fun emptyState(
        activity: Activity,
        tag: String,
        icon: WorkspaceIcon,
        title: String,
        description: String,
        formatLabels: List<String>,
        actions: List<AppUi.ActionSpec>,
    ): LinearLayout

    fun stateCard(
        activity: Activity,
        tag: String,
        title: String,
        description: String,
    ): LinearLayout

    fun metricGrid(
        activity: Activity,
        metrics: List<Pair<String, String>>,
    ): LinearLayout
}
```

`scroll` must set `isFillViewport = true` and attach the page with match-parent width and wrap-content height. `ReportWorkspaceUi` owns a private rounded-background helper because `AppUi.rounded` is intentionally private. Empty-state cards use a minimum content height that balances a compact phone viewport without fixed K60/K80 pixels. A small custom-drawn `WorkspaceIconView` renders a report sheet or chart using existing scheme A colors so the design does not depend on emoji fonts.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the same `ReportActivitiesSmokeTest` command and expect all focused tests to pass.

- [ ] **Step 5: Commit the shared component**

```bash
git add app/src/main/kotlin/com/redmiklab/app/ReportWorkspaceUi.kt app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt
git commit -m "feat: add report workspace components"
```

### Task 2: Spacious History Report Picker

**Files:**
- Create: `app/src/main/kotlin/com/redmiklab/app/ReportHistoryChoiceAdapter.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportActivitySupport.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt`

**Interfaces:**
- Consumes: `DiagnosticRunEntity`, `ReportActivitySupport.historyDisplayName`, `ReportText.time`, and `AppUi` design tokens.
- Produces: `ReportHistoryChoiceAdapter(context, runs)` whose `getView` returns a card row and whose `getItem(position)` returns the corresponding `DiagnosticRunEntity`.

- [ ] **Step 1: Write failing history-row tests**

Instantiate the adapter with a named six-hour strict run and assert the real rendered row:

```kotlin
val row = adapter.getView(0, null, FrameLayout(activity)) as ViewGroup
val texts = allViews(row).filterIsInstance<TextView>().map { it.text.toString() }

assertTrue(row.minimumHeight >= AppUi.run { activity.dp(76) })
assertTrue("七月夜间测试" in texts)
assertTrue(texts.any { "2026-07-25 01:00" in it })
assertTrue(texts.any { "严格模式" in it && "已完成" in it })
assertTrue(allViews(row).any { it.tag == "history_choice_chevron" })
```

Add a mutation-focused assertion that a recycled row receives the second report's filename, preventing stale text reuse.

- [ ] **Step 2: Run the focused test and verify RED**

Expected: compilation fails because `ReportHistoryChoiceAdapter` is missing.

- [ ] **Step 3: Implement the card adapter and connect it to the dialog**

Each row uses a white rounded card, thin border, 12dp inner padding, at least 76dp height, and an 8dp bottom gap supplied by the row wrapper. Filename supports two lines with end ellipsis; metadata lines remain smaller and muted. Replace `SchemeAChoiceAdapter` in `chooseHistory` with this adapter, remove the 1dp divider, and keep the original click-to-select callback.

Constrain the `ListView` to a maximum height derived from the current display height so the dialog remains inside system safe areas and long history lists scroll internally.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run `ReportActivitiesSmokeTest` and confirm the existing history selection label behavior still passes.

- [ ] **Step 5: Commit the picker redesign**

```bash
git add app/src/main/kotlin/com/redmiklab/app/ReportHistoryChoiceAdapter.kt app/src/main/kotlin/com/redmiklab/app/ReportActivitySupport.kt app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt
git commit -m "style: redesign history report picker"
```

### Task 3: Analysis Workspace States and Result Hierarchy

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportAnalysisActivity.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt`

**Interfaces:**
- Consumes: all `ReportWorkspaceUi` interfaces from Task 1 and existing `ReportAnalyzer` output.
- Produces: analysis page tags `analysis_workspace_empty`, `analysis_workspace_loading`, `analysis_report_summary`, and `analysis_metric_grid` for real state verification.

- [ ] **Step 1: Write failing analysis-page tests**

Assert the initial activity uses a fill-viewport scroll container, contains `analysis_workspace_empty`, displays ZIP/JSON support labels, and has two `NEUTRAL` source buttons. Add a reflection-driven render test with a real `CompleteReport` fixture and assert the empty state is replaced by the report summary and two-column metric grid.

- [ ] **Step 2: Run the focused test and verify RED**

Expected: the new empty-state and result hierarchy tags are absent.

- [ ] **Step 3: Implement the analysis states**

Move both source actions into `ReportWorkspaceUi.emptyState`. Replace the bare loading text with `stateCard`. In `render`, create a report summary card followed by a two-column metric grid for system mobile bytes, tunnel observed bytes, average download, and average signal. Keep `SignalChartView`, `TrafficPieView`, top-app lines, and findings logic unchanged.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the activity smoke tests and confirm all analysis assertions pass.

- [ ] **Step 5: Commit the analysis redesign**

```bash
git add app/src/main/kotlin/com/redmiklab/app/ReportAnalysisActivity.kt app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt
git commit -m "style: balance analysis report workspace"
```

### Task 4: Import Workspace and Comparison Surface Continuity

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportImportActivity.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportCompareActivity.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt`

**Interfaces:**
- Consumes: `ReportWorkspaceUi.scroll`, `emptyState`, `stateCard`, existing local picker callbacks, and existing comparison state restoration.
- Produces: tags `import_workspace_empty`, `import_report_summary`, `compare_source_a`, `compare_source_b`, and `compare_result_card`.

- [ ] **Step 1: Write failing import and comparison tests**

Assert:

```kotlin
assertEquals(AppUi.ActionTone.NEUTRAL, button(import, "选择本地报告").tag)
assertNotNull(allViews(import.window.decorView as ViewGroup).firstOrNull {
    it.tag == "import_workspace_empty"
})
```

For comparison, assert its root scroll fills the viewport, A and B source cards have `AppUi.SURFACE` backgrounds, and their tags are present independently of selection state.

- [ ] **Step 2: Run focused tests and verify RED**

Expected: import still uses `TONAL`, no import workspace card exists, and comparison tags are absent.

- [ ] **Step 3: Implement import and comparison layouts**

Move the import action into a report workspace empty-state card and change its tone to `NEUTRAL`. On successful import replace the empty state with a structured summary; on failure show an error state card with a neutral “重新选择本地报告” action.

Wrap comparison with `ReportWorkspaceUi.scroll`, tag both source cards and the result card, and retain white `SURFACE` cards over the full-height blue-gray page. Do not alter report A/B assignment, saved-state restoration, or comparison calculation.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run `ReportActivitiesSmokeTest` and confirm import, compare, insets, history, and source-button tests all pass.

- [ ] **Step 5: Commit import and comparison changes**

```bash
git add app/src/main/kotlin/com/redmiklab/app/ReportImportActivity.kt app/src/main/kotlin/com/redmiklab/app/ReportCompareActivity.kt app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt
git commit -m "style: refine report import and comparison"
```

### Task 5: Full Verification and Redmi Device Acceptance

**Files:**
- Modify only if verification exposes a regression in files already listed above.
- Create: `docs/superpowers/progress/2026-08-01/report-workspace-layout.md`

**Interfaces:**
- Consumes: completed report workspace implementation and currently connected Redmi device.
- Produces: passing build evidence, installed APK, true-device screenshots, and a progress record.

- [ ] **Step 1: Run the full project verification**

```bash
./gradlew test lintDebug assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL` with no failed test or Lint task.

- [ ] **Step 2: Verify the device and preserve diagnostic data**

Use the configured Android SDK `adb` to identify the connected K60/K80. Before installation, query the app database read-only and record the diagnostic run count plus any `RUNNING` or `FINALIZING` row. Do not install while an active run exists.

- [ ] **Step 3: Install the debug APK and inspect crash logs**

Install with `adb install -r`, launch each report page through the app, and confirm logcat contains no `FATAL EXCEPTION` for the package.

- [ ] **Step 4: Capture and inspect true-device screenshots**

Capture:

- analysis empty workspace;
- analysis history picker;
- import empty workspace;
- comparison A/B sources;
- one populated analysis result when an existing history record is available.

Verify full-height background continuity, status/navigation safe areas, relaxed list spacing, source-button consistency, and absence of clipped controls.

- [ ] **Step 5: Confirm database preservation and write progress evidence**

Re-query the diagnostic database read-only after installation. The diagnostic-run count must match the pre-install value. Record commands, counts, screenshots, and any evidence limitations in `docs/superpowers/progress/2026-08-01/report-workspace-layout.md`.

- [ ] **Step 6: Commit acceptance evidence**

```bash
git add docs/superpowers/progress/2026-08-01/report-workspace-layout.md
git commit -m "docs: record report workspace acceptance"
```
