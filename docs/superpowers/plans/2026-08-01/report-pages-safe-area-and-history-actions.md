# 报告页面安全区与历史记录操作层级实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复四个报告页面的系统栏重叠，直接展示历史诊断操作和报告文件名，并统一比较、分析页面的数据来源按钮。

**Architecture:** `AppUi.page` 统一处理状态栏和导航栏 Insets，使所有报告页面自动适配三键及手势导航。`ReportHistoryActivity` 将常用操作与按需加载的统计详情分离，`ReportActivitySupport` 集中生成历史记录的可读名称和选择标签。数据加载、导出、比较和分析回调保持不变。

**Tech Stack:** Kotlin、Android Views、WindowInsets、Room、Robolectric、JUnit 4、Gradle。

## Global Constraints

- 只调整 Android 端页面布局、文字摘要和按钮视觉角色。
- 不修改诊断、采集、报告生成、导入、分析或比较的数据逻辑。
- 使用 JDK 17，`ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`。
- 文档继续使用 `docs/superpowers/{specs,plans}/YYYY-MM-DD/名称.md` 目录规范。
- 覆盖安装前必须确认设备没有 `RUNNING` 或 `FINALIZING` 诊断。

---

### Task 1: 为报告页面统一安装系统安全区

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/AppUi.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt`

**Interfaces:**
- Produces: `AppUi.page(...)` 返回标签为 `scheme_a_report_page` 的页面根布局。
- Consumes: `WindowInsets.Type.statusBars()` 与 `WindowInsets.Type.navigationBars()`。

- [ ] **Step 1: 写失败测试**

为四个报告 Activity 分发顶部 48px、底部 72px 的 Insets，断言页面 padding 是基础值加系统安全区，并重复分发一次以确认不会累加。

```kotlin
@Test
fun report_pages_add_status_and_navigation_insets_without_accumulating_them() {
    reportActivities.forEach { type ->
        val activity = Robolectric.buildActivity(type).setup().get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val page = allViews(content).first { it.tag == "scheme_a_report_page" }
        val insets = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 48, 0, 0))
            .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 72))
            .build()

        content.dispatchApplyWindowInsets(insets)
        content.dispatchApplyWindowInsets(insets)

        assertEquals(AppUi.run { activity.dp(14) } + 48, page.paddingTop)
        assertEquals(AppUi.run { activity.dp(24) } + 72, page.paddingBottom)
    }
}
```

- [ ] **Step 2: 运行测试确认 RED**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.ReportActivitiesSmokeTest.report_pages_add_status_and_navigation_insets_without_accumulating_them
```
Expected: FAIL，因为现有 `AppUi.page` 只有 14dp/24dp 固定 padding，且没有页面标签。

- [ ] **Step 3: 实现 Insets**

在 `AppUi.page` 中保存基础 padding，设置页面标签，并以每次收到的系统栏 Insets 重新计算：

```kotlin
val horizontal = activity.dp(16)
val baseTop = activity.dp(14)
val baseBottom = activity.dp(24)
tag = "scheme_a_report_page"
setPadding(horizontal, baseTop, horizontal, baseBottom)
setOnApplyWindowInsetsListener { view, insets ->
    val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        insets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
    } else {
        Insets.of(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
    }
    view.setPadding(horizontal, baseTop + bars.top, horizontal, baseBottom + bars.bottom)
    insets
}
```

调用 `requestApplyInsets()`，保证页面附着后主动获取系统安全区。

- [ ] **Step 4: 运行测试确认 GREEN**

运行 Task 1 的定向测试，Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/redmiklab/app/AppUi.kt app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt
git commit -m "fix: inset report pages from system bars"
```

---

### Task 2: 重排历史诊断卡片与选择标签

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportActivitySupport.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportHistoryActivity.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt`

**Interfaces:**
- Produces: `ReportActivitySupport.historyDisplayName(run: DiagnosticRunEntity): String`。
- Produces: `ReportActivitySupport.historyChoiceLabel(run: DiagnosticRunEntity): String`。
- Produces: 历史卡片标签 `history_details_toggle`、`history_details_content`、`history_actions`。

- [ ] **Step 1: 写历史名称失败测试**

用固定 `DiagnosticRunEntity` 断言已保存名称优先，空名称回退现有默认命名；选择标签包含名称、开始时间、中文模式和中文状态，不再把 `23013RK75C` 单独作为主要文字。

```kotlin
assertEquals("七月夜间测试", ReportActivitySupport.historyDisplayName(namedRun))
assertTrue(ReportActivitySupport.historyDisplayName(unnamedRun).startsWith("RedmiKLab-"))
assertEquals(
    "七月夜间测试\n2026-07-25 01:00 · 严格模式 · 已完成",
    ReportActivitySupport.historyChoiceLabel(namedRun),
)
```

- [ ] **Step 2: 写历史卡片失败测试**

通过反射调用 `runCard`，断言文件名、两行摘要和四个操作初始可见；详情初始隐藏；整张卡片不再承担点击展开；标题行右侧存在“查看详情”。

```kotlin
val card = runCard.invoke(activity, namedRun) as ViewGroup
val views = allViews(card)
assertTrue(views.filterIsInstance<TextView>().any { it.text == "七月夜间测试" })
listOf("分析报告", "导出报告", "修改文件名", "与其他报告比较").forEach { label ->
    assertEquals(View.VISIBLE, views.filterIsInstance<TextView>().first { it.text == label }.visibility)
}
assertEquals(View.GONE, views.first { it.tag == "history_details_content" }.visibility)
assertFalse(card.hasOnClickListeners())
assertEquals("查看详情", (views.first { it.tag == "history_details_toggle" } as TextView).text)
```

- [ ] **Step 3: 运行测试确认 RED**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.ReportActivitiesSmokeTest
```
Expected: FAIL，因为现有卡片把统计和四个按钮共同隐藏，且选择标签仍为时间、设备代码和英文状态。

- [ ] **Step 4: 实现历史名称与选择标签**

在 `ReportActivitySupport` 中加入纯格式化函数：

```kotlin
fun historyDisplayName(run: DiagnosticRunEntity): String =
    run.reportBaseName.ifBlank { defaultBaseName(run) }

fun historyChoiceLabel(run: DiagnosticRunEntity): String =
    "${historyDisplayName(run)}\n${ReportText.time(run.startedAtEpochMs)} · " +
        "${if (run.runtimeMode == "STRICT") "严格模式" else "标准模式"} · " +
        if (run.status == "COMPLETED") "已完成" else "已中断"
```

`chooseHistory` 使用 `historyChoiceLabel` 生成列表文字。

- [ ] **Step 5: 实现历史卡片布局**

将卡片标题改为实际起止时间；标题行追加小型“查看详情”文字按钮。卡片正文依次加入报告文件名、两行摘要、始终可见的两行两列操作区，最后加入初始为 `GONE` 的详情区。

详情按钮只切换详情区和自身文字：

```kotlin
toggle.setOnClickListener {
    val expanding = details.visibility != View.VISIBLE
    details.visibility = if (expanding) View.VISIBLE else View.GONE
    toggle.text = if (expanding) "收起详情" else "查看详情"
    if (expanding && details.childCount == 0) populateDetails(run, details)
}
```

`populateDetails` 只写入统计摘要，不再创建四个操作按钮。

- [ ] **Step 6: 运行测试确认 GREEN**

运行 `ReportActivitiesSmokeTest`，Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/kotlin/com/redmiklab/app/ReportActivitySupport.kt app/src/main/kotlin/com/redmiklab/app/ReportHistoryActivity.kt app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt
git commit -m "fix: expose history report actions"
```

---

### Task 3: 统一比较与分析页面的数据来源按钮

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportCompareActivity.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportAnalysisActivity.kt`
- Test: `app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt`

**Interfaces:**
- Consumes: `AppUi.ActionTone.NEUTRAL`。
- Produces: 比较和分析页面的所有数据来源按钮均为白底描边视觉角色。

- [ ] **Step 1: 写失败测试**

扩展 `report_source_actions_are_not_implicitly_primary`，断言分析页两个按钮及比较页 A/B 四个按钮全部为 `NEUTRAL`；导入页的单一入口维持当前 `TONAL`，不在本次范围内。

```kotlin
assertEquals(AppUi.ActionTone.NEUTRAL, button(analysis, "从历史记录选择").tag)
assertEquals(AppUi.ActionTone.NEUTRAL, button(analysis, "从本地文件选择（最近 / 浏览）").tag)
assertEquals(2, buttons(compare, "从历史记录选择").count { it.tag == AppUi.ActionTone.NEUTRAL })
assertEquals(2, buttons(compare, "从本地选择（最近 / 浏览）").count { it.tag == AppUi.ActionTone.NEUTRAL })
```

- [ ] **Step 2: 运行测试确认 RED**

运行该定向测试，Expected: FAIL，因为历史来源按钮当前是 `TONAL`。

- [ ] **Step 3: 修改按钮色调**

把 `ReportCompareActivity.sourceSection` 和 `ReportAnalysisActivity.onCreate` 中“从历史记录选择”的 `tone` 改为 `AppUi.ActionTone.NEUTRAL`，不改点击回调。

- [ ] **Step 4: 运行测试确认 GREEN**

运行该定向测试，Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/redmiklab/app/ReportCompareActivity.kt app/src/main/kotlin/com/redmiklab/app/ReportAnalysisActivity.kt app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt
git commit -m "style: unify report source actions"
```

---

### Task 4: 完整验证与真机同步

**Files:**
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: Tasks 1–3 生成的报告页面布局。

- [ ] **Step 1: 完整自动化验证**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew test lintDebug assembleDebug
git diff --check
```
Expected: `BUILD SUCCESSFUL`，且差异检查无输出。

- [ ] **Step 2: 安装前保护诊断数据**

使用 ADB 只读复制设备数据库及 WAL，查询 `diagnostic_runs` 中 `status in ('RUNNING','FINALIZING')`。若存在活动记录则停止安装并报告；没有活动记录才继续。

- [ ] **Step 3: 覆盖安装并检查四个页面**

覆盖安装 `app-debug.apk`，依次检查：

- 四个报告页面标题不与状态栏重叠；
- 页面最后一项与三键/手势导航区域有间距；
- 历史卡片直接显示文件名和四个操作；
- “查看详情”只展开统计文字；
- 比较和分析页面的数据来源按钮颜色一致。

- [ ] **Step 4: 截图、日志和工作区验收**

保存真机截图，确认 `logcat` 无 `FATAL EXCEPTION`，并运行：

```bash
git status --short
```

Expected: 工作区为空。
