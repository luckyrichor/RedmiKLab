# 连续不重叠应用流量窗口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让诊断期间的应用流量窗口连续、不重叠，并让报告按应用累计全部窗口。

**Architecture:** 诊断模块使用独立的 `TrafficWindowPlanner` 保存上一次窗口终点；服务在每次实际快照时向它请求下一个区间，再将该区间传给系统流量查询。报告模块以包名聚合已保存的分段记录。

**Tech Stack:** Kotlin、Android `NetworkStatsManager`、Room、JUnit 4。

## Global Constraints

- Android minSdk 26；K60/K80 使用同一 APK。
- 流量仅统计移动数据，且标记为时段级近似统计。
- 文档按 `docs/superpowers/{specs,plans}/YYYY-MM-DD/` 存放。

---

### Task 1: 分段窗口规划器

**Files:**
- Create: `feature/diagnostics/src/main/kotlin/com/redmiklab/diagnostics/TrafficWindowPlanner.kt`
- Create: `feature/diagnostics/src/test/kotlin/com/redmiklab/diagnostics/TrafficWindowPlannerTest.kt`

- [ ] 写入失败测试，断言从 18:14:40 开始，18:19、18:24、18:25 三次取样得到连续区间。
- [ ] 运行 `:feature:diagnostics:testDebugUnitTest --tests com.redmiklab.diagnostics.TrafficWindowPlannerTest`，预期因类不存在失败。
- [ ] 实现规划器并复跑测试，预期通过。

### Task 2: 服务与报告接入

**Files:**
- Modify: `app/src/main/kotlin/com/redmiklab/app/NightDiagnosticService.kt`
- Modify: `feature/reports/src/main/kotlin/com/redmiklab/reports/RoomReportExporter.kt`

- [ ] 用规划器产生的区间调用 `AndroidTrafficStatsSource.readWindow`，并以服务实际启动时刻初始化规划器。
- [ ] 报告按包名累计多个不重叠窗口的字节数来判断流量最高应用。
- [ ] 执行 `:app:test :app:lint :app:assembleDebug`，预期成功。

### Task 3: 真机验收

**Files:**
- Modify: 无。

- [ ] 覆盖安装 `app/build/outputs/apk/debug/app-debug.apk` 到已连接手机。
- [ ] 使用一个至少跨越一次五分钟采样和结束时刻的测试窗口导出报告，检查 `traffic.csv` 的相邻时间窗首尾相接。
