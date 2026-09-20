# RedmiKLab

面向 Redmi K 系列设备的开发与测试项目。**第一期**是给 Redmi K60 / K80 用的 Android **移动网络夜间诊断 App**。

## 解决什么问题

手机在夜里挂着，第二天发现流量跑了很多、或者网络时断时续 —— 但事发时人在睡觉，没有任何现场信息。这个 App 在设定的夜间窗口内自动采集网络状态、连通性、吞吐和分应用流量，早上给出一份可导出的报告。

设计上有几条硬约束，它们决定了这个项目的大部分复杂度：

- **不 Root，不依赖常驻 ADB** —— 必须在普通用户权限下工作
- **连接级采集走本地 VPN**，需用户显式启用，且与 Clash Meta 等其他 VPN **互斥**（Android 同时只允许一个 VPN）
- **不能依赖连续数小时的 `dataSync` 前台服务** —— 现代 Android 会杀掉它
- **数据默认只留在设备上**

## 两种模式

| | 标准模式 | 严格模式 |
|---|---|---|
| 窗口上限 | 最长 6 小时（预留 120 秒安全收尾） | 可设更长 |
| 连接级采集 | 不启用 | 必须启用 |
| 唤醒锁 | 无 | 有上限的 CPU 唤醒锁，提高采样准时率 |

默认采样节奏：状态快照 5 分钟、连通性 10 分钟、吞吐 30 分钟；单次吞吐最多 5 MB，每晚最多 1 GB。窗口、间隔、流量预算都可配置。

## 模块划分

```
app/                    应用入口、权限、界面，以及 C++ 转发引擎（JNI）
core/model/             稳定的数据模型与诊断规则（19 个 Kotlin 文件）
core/storage/           本地持久化与导出
feature/diagnostics/    采集与夜间运行编排（40 个文件）
feature/reports/        报告生成、导出与双机对比（24 个文件）
```

### app/src/main/cpp —— 本地 VPN 转发引擎

第一期最重的技术部分，约 45 个 C++ 文件。本地 VPN 拿到的是**原始 IP 包**，要让流量正常走通就得自己实现一套用户态网络栈：

- `packet_parser` — IP/TCP/UDP 包解析
- `flow_table` — 连接流表
- `tcp_state` / `tcp_proxy_session` / `tcp_packet_builder` — TCP 状态机与代理会话
- `direct_tcp_socket` / `direct_udp_socket` / `direct_forwarder` — 直连转发
- `tls_sni_parser` — 从 TLS 握手里取 SNI（用于识别目标域名，不解密内容）
- `forwarder_engine` / `forwarder_runtime` — 引擎与运行时
- `checksum` — 校验和计算

每个模块都有配套的 `*_test.cpp`。

### feature/diagnostics —— 运行编排

夜间跑任务的难点不在采集本身，而在**在受限的 Android 后台环境里准时跑完并安全收尾**。相关实现：`DiagnosticWindow`、`RunDeadline`、`RunState`、`RunConfigurationLock`、`SnapshotSchedule`、`ProbePlanner`、`NextActionPlanner`、`StartAlarmPolicy`、`KeepAwakeLease`、`BudgetTracker`（流量预算）、`FinalSnapshotGate`（收尾快照闸门）。

数据源：`AndroidNetworkSnapshotSource`（网络状态）、`AndroidTrafficStatsSource` + `UidTrafficAggregator`（分应用流量）、`HttpProbeRunner`（连通性）、`ThroughputCalculator`。

### feature/reports —— 报告

导出 ZIP 至少含**离线 HTML + CSV + JSON** 三种格式：`CompleteReportHtmlRenderer`、`ProbeCsvRenderer`、`CompleteReportJson`、`LineChartRenderer`（图表）、`MediaTimelineBuilder`（媒体时间线）、`LayeredTrafficReportBuilder`（分层流量）、`ReportComparison`（**双机对比** —— K60 与 K80 同夜运行结果比对）、`ReportZipExporter`。

## 隐私边界

这部分是明确划定的，不是顺带说明：

- **不保存**网络包负载、网页请求正文、账号凭证、视频二进制内容
- 诊断期间**保存**网络参与 App 与媒体相关 App 的完整通知字段和媒体元数据（用户已授权）；仅作为前台背景且无网络/媒体活动的 App 不保存完整通知
- 报告中的应用流量只表达**时段级关联**，不声称逐包归属或因果结论

## 本地构建

需要 JDK 17、Android SDK Platform 37.0、Build Tools 36.0.0。使用固定版本的 Gradle Wrapper。

```zsh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew test :app:assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

> 上面的路径写法是 macOS + Homebrew 的。Linux/Windows 请自行替换 `JAVA_HOME` 与 `ANDROID_HOME`。

## 工程约定

完整约定见 [`AGENTS.md`](AGENTS.md)，其中几条对读代码的人比较重要：

- **测试先行**：先写失败测试、确认失败原因、再写最小实现
- 不在 `main` 上直接开发，功能走隔离工作区和分支
- 「完成」「可构建」「测试通过」的说法必须以**当前命令输出**为依据
- **手机端视觉改版不能只看代码常量或 Robolectric 标签**，必须在目标 Redmi 真机截图逐项核对后才算完成
- 单一 APK 同时支持 K60 与 K80，**不按机型分叉**

真机验证记录在 `docs/superpowers/progress/YYYY-MM-DD/`，设计与计划在 `docs/superpowers/{specs,plans}/`，验收记录在 `docs/acceptance/`。
