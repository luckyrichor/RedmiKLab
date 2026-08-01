# 方案 A 真机高保真实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清除 MIUI 系统控件样式泄漏，使主页面、设置窗口和二级页面在真机上高保真还原已批准的浏览器方案 A。

**Architecture:** `AppUi` 提供自绘扁平操作控件、设置值样式和统一对话框面板；各 Activity 只声明视觉角色并保留现有回调。每个阶段先以 Robolectric 记录缺失行为，再实现并在 K80 真机截图验收。

**Tech Stack:** Kotlin、Android View、Robolectric、Gradle、ADB

## Global Constraints

- 不修改诊断、计划、连接级采集或报告业务逻辑。
- 不迁移到 Compose，不新增 UI 依赖。
- 以 `docs/superpowers/specs/2026-07-23/scheme-a-true-fidelity.md` 为唯一验收依据。
- 安装前必须确认没有 `RUNNING` 或 `FINALIZING` 诊断。

---

### Task 1: 自绘扁平控件与主页面

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/AppUi.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/MainActivity.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/MainActivityLayoutTest.kt`

**Interfaces:**
- Produces: `AppUi.SettingStyle`、`AppUi.addSetting(..., style)`、扁平 `actionButton`

- [ ] 写失败测试：操作控件不是系统 `Button`、高度 36dp、elevation 为 0；参数行有 `›`，模式行有开关，权限行有徽标。
- [ ] 运行 `./gradlew :app:testDebugUnitTest --tests com.redmiklab.app.MainActivityLayoutTest` 并确认因系统 Button 和缺少状态组件而失败。
- [ ] 在 `AppUi` 实现自绘扁平操作控件、专用 `ACTION_BORDER`/`STOP_BORDER` 和设置值样式。
- [ ] 在 `MainActivity` 为参数、模式、权限行指定正确样式并收紧顶部排版。
- [ ] 重跑定向测试并提交。

### Task 2: 自绘设置窗口

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/AppUi.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/DiagnosticSettingsDialogs.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt`

**Interfaces:**
- Consumes: Task 1 扁平操作控件
- Produces: 透明窗口容器内的统一方案 A 面板

- [ ] 写失败测试：快照、测速地址、时间和输出名称窗口不得使用系统标题、系统 Message 和系统对话框按钮。
- [ ] 运行定向测试并确认当前原生 `AlertDialog` 结构导致失败。
- [ ] 实现统一自绘标题、说明、内容和 36dp 取消/保存按钮；保留所有校验与回调。
- [ ] 收紧 NumberPicker、输入框和格式下拉尺寸，隐藏系统默认大间距。
- [ ] 重跑定向测试并提交。

### Task 3: 二级页面与完整验证

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportHistoryActivity.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportCompareActivity.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportAnalysisActivity.kt`
- Modify: `app/src/main/kotlin/com/redmiklab/app/ReportImportActivity.kt`
- Modify: `app/src/test/kotlin/com/redmiklab/app/ReportActivitiesSmokeTest.kt`

**Interfaces:**
- Consumes: Task 1/2 统一组件

- [ ] 以失败测试定位二级页面残留的系统 Button 或不一致尺寸。
- [ ] 全部替换为统一扁平控件，不改变数据读取、比较、分析或导出流程。
- [ ] 运行 `./gradlew test lintDebug assembleDebug`。
- [ ] 复查数据库无活动诊断，覆盖安装到 K80。
- [ ] 截取主页面上部、操作区和设置窗口三张真机图；与方案 A 对照后才提交完成结论。
