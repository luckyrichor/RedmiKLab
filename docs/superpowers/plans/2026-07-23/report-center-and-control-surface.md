# RedmiKLab 控制台与报告中心实施计划

> 使用测试驱动开发；每个行为先写失败测试，再写最小实现。每个独立阶段通过验证后单独提交。

## 任务 1：持久化报告身份与格式

**文件**

- 修改 `core/storage/src/main/java/com/redmiklab/storage/DiagnosticRunEntity.java`
- 修改 `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDao.java`
- 修改 `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDatabase.java`
- 修改 `core/storage/src/main/java/com/redmiklab/storage/DiagnosticDatabaseFactory.java`
- 新增 `core/storage/src/test/kotlin/com/redmiklab/storage/DiagnosticMigration14To15Test.kt`
- 修改 `core/storage/src/test/kotlin/com/redmiklab/storage/DiagnosticDaoTest.kt`
- 修改 `app/src/main/kotlin/com/redmiklab/app/NightDiagnosticService.kt`

**步骤**

1. 写迁移和 DAO 失败测试：默认名称/格式列无损加入，任意 Run 可更新。
2. 数据库升级至 15，字段默认值兼容旧记录。
3. 新 Run 创建时锁定当前模板；运行中允许同步修改。
4. 运行存储模块和 App 相关测试。

## 任务 2：统一完整报告数据模型

**文件**

- 新增 `feature/reports/src/main/kotlin/com/redmiklab/reports/CompleteReport.kt`
- 新增 `feature/reports/src/main/kotlin/com/redmiklab/reports/CompleteReportJson.kt`
- 重构 `feature/reports/src/main/kotlin/com/redmiklab/reports/RoomReportExporter.kt`
- 修改 `feature/reports/src/main/kotlin/com/redmiklab/reports/ReportComparison.kt`
- 新增/修改对应测试

**步骤**

1. 写 JSON 往返、旧 ZIP 兼容、完整字段覆盖失败测试。
2. 从 Room 构建统一 `CompleteReport`。
3. 实现不依赖第三方库的确定性 JSON 编码/解析。
4. ZIP 的 `report.json` 升级为完整结构，同时保留既有 CSV/HTML。
5. 导入器同时接受完整 JSON、ZIP 和旧 schema 摘要。

## 任务 3：四种导出渲染

**文件**

- 新增 `feature/reports/src/main/kotlin/com/redmiklab/reports/ReportFormat.kt`
- 新增 `feature/reports/src/main/kotlin/com/redmiklab/reports/StandaloneReportExporter.kt`
- 扩展 `HtmlReportRenderer.kt`
- 新增 `app/src/main/kotlin/com/redmiklab/app/PdfReportExporter.kt`
- 新增/修改报告测试

**步骤**

1. 写文件名/MIME/扩展名、JSON、HTML 和 PDF 合约失败测试。
2. ZIP 继续调用现有完整包导出。
3. JSON 输出完整模型。
4. HTML 输出离线单文件摘要、图表和重要明细。
5. PDF 使用 Android `PdfDocument` 生成分页摘要并以 Robolectric 验证 PDF 头和非空内容。

## 任务 4：报告分析与比较模型

**文件**

- 新增 `feature/reports/src/main/kotlin/com/redmiklab/reports/ReportAnalysis.kt`
- 扩展 `ReportComparison.kt`
- 新增对应测试

**步骤**

1. 写双报告指标比较、证据约束措辞、信号趋势和流量构成失败测试。
2. 实现本地规则分析与结构化比较结果。
3. 确保无数据时明确显示“无数据”，不编造数值。

## 任务 5：前端设计组件和参数弹窗

**文件**

- 新增 `app/src/main/kotlin/com/redmiklab/app/MainScreenViews.kt`
- 新增 `app/src/main/kotlin/com/redmiklab/app/DiagnosticSettingsDialogs.kt`
- 新增 `app/src/main/kotlin/com/redmiklab/app/ReportNameSettings.kt`
- 重构 `app/src/main/kotlin/com/redmiklab/app/MainActivity.kt`
- 新增对应策略与 Robolectric 测试

**步骤**

1. 写设置摘要、重试单位换算、格式选择、进程提示门控失败测试。
2. 实现六个卡片板块、滚动和导航栏安全边距。
3. 实现快照、端点、开始/结束、文件名弹窗。
4. 实现小问号说明浮层。
5. 保持所有后端按钮回调原义不变。
6. 临时连接提示改为每进程最多一次，通知不变。

## 任务 6：历史诊断导出页面

**文件**

- 新增 `app/src/main/kotlin/com/redmiklab/app/ReportHistoryActivity.kt`
- 新增 `app/src/main/kotlin/com/redmiklab/app/ReportExportCoordinator.kt`
- 修改 `app/src/main/AndroidManifest.xml`
- 新增对应测试

**步骤**

1. 写历史倒序、摘要、展开操作和名称永久修改失败测试。
2. 实现历史列表与展开详情。
3. 实现所选 Run 的格式/文件名导出。
4. 导出窗口修改同步回所选 Run。

## 任务 7：导入、比较与分析页面

**文件**

- 新增 `app/src/main/kotlin/com/redmiklab/app/ReportSource.kt`
- 新增 `app/src/main/kotlin/com/redmiklab/app/ReportCompareActivity.kt`
- 新增 `app/src/main/kotlin/com/redmiklab/app/ReportAnalysisActivity.kt`
- 新增 `app/src/main/kotlin/com/redmiklab/app/ReportImportActivity.kt`
- 修改 `app/src/main/AndroidManifest.xml`
- 新增对应测试

**步骤**

1. 写来源选择、历史预填 A、格式拒绝和状态恢复失败测试。
2. 实现历史记录选择和系统文件选择器。
3. 实现 ZIP/JSON 导入，明确拒绝 HTML/PDF。
4. 实现双报告比较展示。
5. 实现单报告指标、趋势、流量构成、表格和文字分析。

## 任务 8：全量验证、提交和手机验收

**步骤**

1. 运行 `./gradlew test`。
2. 运行 `./gradlew lintDebug`。
3. 运行 `./gradlew assembleDebug`。
4. 检查 Git diff、迁移安全、APK SHA-256。
5. 用 ADB 确认当前连接设备型号和序列号。
6. 读取数据库，确认没有 `RUNNING` 或 `FINALIZING`。
7. `adb install -r` 安装 Debug APK。
8. 启动 App，检查无闪退、主页面分区、历史入口、弹窗和滚动。
9. 将进度记录写入 `docs/superpowers/progress/2026-07-23/` 并提交最终状态。
