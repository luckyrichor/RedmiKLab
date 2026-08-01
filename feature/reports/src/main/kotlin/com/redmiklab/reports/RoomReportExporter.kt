package com.redmiklab.reports

import com.redmiklab.storage.DiagnosticDatabase
import com.redmiklab.model.EndpointClassifier
import com.redmiklab.model.TrafficReconciliation
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RoomReportExporter(private val database: DiagnosticDatabase) {
    fun export(runId: String, output: OutputStream) {
        val dao = database.diagnosticDao()
        val run = requireNotNull(dao.runById(runId)) { "Unknown diagnostic run: $runId" }
        val snapshots = dao.snapshotsFor(runId)
        val probes = dao.probesFor(runId)
        val attempts = dao.probeAttemptsFor(runId)
        val traffic = dao.appTrafficFor(runId)
        val flows = dao.connectionFlowsFor(runId)
        val events = dao.eventsFor(runId)
        val screenWakeRequestCount = events.count { it.eventType == "NETWORK_SCREEN_WAKE_REQUESTED" }
        val reconnectStartedCount = events.count { it.eventType == "NETWORK_RETRY_STARTED" }
        val reconnectExhaustedCount = events.count { it.eventType == "NETWORK_RETRIES_EXHAUSTED" }
        val evidence = dao.appEvidenceFor(runId)
        val accounting = dao.trafficAccountingFor(runId)
        val lifecycle = dao.networkLifecycleFor(runId)
        val gaps = dao.captureGapsFor(runId)
        val behaviorRows = MediaTimelineBuilder.build(flows, evidence)
        val systemMobileBytes = snapshots.sumOf { it.totalMobileBytes }
        val tunnelObservedBytes = accounting.filter { it.layer == "TUNNEL_OBSERVED" }.sumOf { it.bytes }
        val probeBytes = accounting.filter { it.layer == "ACTIVE_PROBE" }.sumOf { it.bytes }
        val heartbeatBytes = accounting.filter { it.layer == "HEARTBEAT" }.sumOf { it.bytes }
        val reconciliation = TrafficReconciliation.reconcile(
            systemMobileBytes,
            tunnelObservedBytes,
            probeBytes,
            heartbeatBytes,
        )
        val averageDownload = probes.mapNotNull { it.downloadMbps }.average().takeIf { !it.isNaN() }
        val topApp = traffic
            .groupBy { it.packageName to it.displayName }
            .map { (identity, windows) -> AggregatedAppTraffic(identity.second, windows.sumOf { it.mobileBytes }) }
            .maxByOrNull { it.mobileBytes }
        val findings = buildList {
            if (run.status == "INTERRUPTED") add("诊断未能正常收尾，报告可能缺少最终快照；已保留此前已写入的数据。")
            if (probes.isEmpty()) add("没有成功写入主动探测记录；请检查移动数据、探测端点和夜间前台服务权限。")
            if (probes.any { it.failure != null }) add("部分主动探测失败，详见 probes.csv 中的 failure 字段。")
            if (attempts.isNotEmpty()) add("已记录 ${attempts.size} 次主动测速尝试，包含主备端点与每次失败阶段，详见 probe_attempts.csv。")
            if (events.any { it.eventType == "PROBE_NETWORK_DIAGNOSTIC" }) add("主动测速已记录默认网络、蜂窝候选、系统能力与选择结果，详见 diagnostic_events.csv。")
            if (flows.isNotEmpty()) add("已记录 ${flows.size} 条连接元数据；不包含请求内容，详见 connection_flows.csv。")
            if (flows.any { it.endpointHost != null }) add("已从可见的 TLS ClientHello SNI 识别部分域名；未解密 HTTPS 内容。")
            if (flows.any { it.endpointHost == null }) add("部分连接没有可见域名，常见原因包括 QUIC、ECH、复用连接或采集从连接中途开始；这些记录仅保留目标 IP。")
            if (evidence.any { it.evidenceType.startsWith("MEDIA_") }) add("已取得媒体会话授权数据，并按回调时间保留变化；可结合前台、锁屏与连接状态判断播放行为。")
            if (evidence.none { it.evidenceType.startsWith("MEDIA_") }) add("未取得活动媒体会话；行为判断会退化为连接、屏幕和前台状态线索。")
            if (gaps.isNotEmpty()) add("连接级采集出现 ${gaps.size} 个盲区；盲区内系统流量不能精确归属到连接，详见 capture_gaps.csv。")
            if (lifecycle.isNotEmpty()) add("已记录 ${lifecycle.size} 次网络守护状态变化，包含物理网络丢失、重试与恢复。")
            if (screenWakeRequestCount > 0) add("亮屏辅助重连已执行：亮屏请求 $screenWakeRequestCount 次，实际开始重连 $reconnectStartedCount 次；锁屏不会被解除。")
            if (reconnectExhaustedCount > 0) add("有 $reconnectExhaustedCount 轮主动重连达到 5 次上限；之后仍保持被动监听新物理移动网络。")
            if (accounting.isNotEmpty()) add("流量已按系统统计、隧道观察、套接字转发、测速和心跳分层，不同口径不可简单相加。")
            if (events.any { it.eventType == "FOREGROUND_SERVICE_TIMEOUT" }) add("Android 曾终止单次后台任务；恢复事件与已保存数据见 diagnostic_events.csv。")
            behaviorRows.groupingBy { it.classification }.eachCount().forEach { (classification, count) ->
                add("行为证据分类 $classification：$count 条；这是概率性判断，不等同于解密后的请求内容。")
            }
            if (snapshots.any { it.trafficCollectionStatus != "SUCCESS" }) add("部分应用流量统计未成功取得；请查看 snapshots.csv 的 traffic_collection_status 字段，不能把零字节视为零流量。")
            if (topApp != null) add("系统统计的连续不重叠时段内流量最高的应用：${topApp.displayName}（${topApp.mobileBytes} 字节）。这仅表示时段级关联。")
        }
        val summary = ReportSummary(
            runId, "夜间移动网络诊断", "报告展示的是时段级关联线索；关联不等于因果。",
            metrics = listOf(
                ReportMetric("运行状态", run.status),
                ReportMetric("运行模式", if (run.runtimeMode == "STRICT") "严格模式" else "标准模式"),
                ReportMetric("计划窗口", "${run.plannedStartEpochMs} – ${run.plannedEndEpochMs}"),
                ReportMetric("实际窗口", "${run.startedAtEpochMs} – ${run.endedAtEpochMs}"),
                ReportMetric("网络状态快照", "${snapshots.size} 条"),
                ReportMetric("快照调度延迟", snapshots.takeIf { it.isNotEmpty() }?.let { rows -> "平均 ${rows.map { it.delayMs }.average().toLong()} ms；最大 ${rows.maxOf { it.delayMs }} ms" } ?: "无数据"),
                ReportMetric("主动探测", "${probes.size} 轮；${attempts.size} 次尝试"),
                ReportMetric("测速调度延迟", probes.takeIf { it.isNotEmpty() }?.let { rows -> "平均 ${rows.map { it.delayMs }.average().toLong()} ms；最大 ${rows.maxOf { it.delayMs }} ms" } ?: "无数据"),
                ReportMetric("平均下载吞吐", averageDownload?.let { "%.2f Mbps".format(java.util.Locale.US, it) } ?: "无有效数据"),
                ReportMetric("5G 快照", "${snapshots.count { it.radioTechnology == "FiveG" }} 条"),
                ReportMetric("应用流量统计", "${traffic.size} 条（时段级近似统计）"),
                ReportMetric("连接级元数据", "${flows.size} 条（不含内容）"),
                ReportMetric("媒体与通知证据", "${evidence.size} 条"),
                ReportMetric("网络生命周期", "${lifecycle.size} 条；采集盲区 ${gaps.size} 个"),
                ReportMetric("亮屏辅助重连", "亮屏请求 $screenWakeRequestCount 次；实际重连 $reconnectStartedCount 次；耗尽 $reconnectExhaustedCount 轮"),
                ReportMetric("分层流量记录", "${accounting.size} 条"),
                ReportMetric("系统移动流量", "$systemMobileBytes 字节（Android 近似统计）"),
                ReportMetric("隧道观察流量", "$tunnelObservedBytes 字节（包含未成功转发的尝试）"),
                ReportMetric("统计口径差值", "${reconciliation.unexplainedBytes} 字节；比例 ${"%.2f%%".format(java.util.Locale.US, reconciliation.differenceRatio * 100)}"),
                ReportMetric("流量统计状态", snapshots.groupingBy { it.trafficCollectionStatus }.eachCount().entries.joinToString { "${it.key}:${it.value}" }),
            ),
            findings = findings,
            chartSvg = LineChartRenderer.render(probes.map { it.downloadMbps }),
            sections = listOf(
                ReportSection(
                    "数据完整性",
                    listOf(
                        "快照 ${snapshots.size} 条；连接级记录 ${flows.size} 条；分层记账 ${accounting.size} 条。",
                        "连接级采集盲区 ${gaps.size} 个；盲区中的系统移动流量可能无法归属到具体连接。",
                    ),
                ),
                ReportSection(
                    "网络丢失与恢复",
                    listOf(
                        "网络守护状态变化 ${lifecycle.size} 条。",
                        "亮屏辅助重连：亮屏请求 $screenWakeRequestCount 次；实际开始重连 $reconnectStartedCount 次；重试耗尽 $reconnectExhaustedCount 轮。",
                        "主动重连结束后仍会保持被动监听；详情见 network_lifecycle.csv。",
                    ),
                ),
                ReportSection(
                    "流量口径对照",
                    buildList {
                        add("ANDROID_SYSTEM_MOBILE：$systemMobileBytes 字节；这是 Android 按时间窗统计的近似移动流量。")
                        accounting.groupBy { it.layer }.entries.sortedBy { it.key }
                            .forEach { (layer, rows) -> add("$layer：${rows.sumOf { it.bytes }} 字节；该口径不得与其他层直接相加。") }
                        add("系统统计减去隧道观察、主动测速和心跳后的差值：${reconciliation.unexplainedBytes} 字节。差值可能来自覆盖窗口、统计粒度、协议开销或采集盲区。")
                    },
                ),
                ReportSection(
                    "媒体、通知与行为证据",
                    listOf(
                        "媒体事件 ${evidence.count { it.evidenceType.startsWith("MEDIA_") }} 条；通知事件 ${evidence.count { it.evidenceType.startsWith("NOTIFICATION_") }} 条。",
                        "行为分类 ${behaviorRows.size} 条；无法解释的后台上传 ${behaviorRows.count { it.classification == "UNEXPLAINED_BACKGROUND_UPLOAD" }} 条。",
                        "无法读取 HTTPS 正文，因此预加载、广告、日志和后台上传均为证据分类，不代表已识别内容本身。",
                    ),
                ),
            ),
        )
        ZipOutputStream(output).use { zip ->
            write(zip, "report.html", HtmlReportRenderer.render(summary))
            write(zip, "traffic_summary.csv", LayeredTrafficReportBuilder.trafficSummary(accounting, systemMobileBytes))
            write(zip, "system_mobile_traffic.csv", LayeredTrafficReportBuilder.systemMobileTraffic(traffic))
            write(zip, "tunnel_observed_traffic.csv", LayeredTrafficReportBuilder.tunnelObserved(accounting))
            write(zip, "forwarding_outcomes.csv", LayeredTrafficReportBuilder.forwardingOutcomes(accounting))
            write(zip, "probe_traffic.csv", LayeredTrafficReportBuilder.layer(accounting, "ACTIVE_PROBE"))
            write(zip, "heartbeat_traffic.csv", LayeredTrafficReportBuilder.layer(accounting, "HEARTBEAT"))
            write(zip, "capture_gaps.csv", LayeredTrafficReportBuilder.captureGaps(gaps))
            write(zip, "network_lifecycle.csv", LayeredTrafficReportBuilder.networkLifecycle(lifecycle))
            write(zip, "endpoint_observations.csv", LayeredTrafficReportBuilder.endpointObservations(flows))
            write(zip, "media_events.csv", LayeredTrafficReportBuilder.evidence(evidence, "MEDIA_"))
            write(zip, "notification_events.csv", LayeredTrafficReportBuilder.evidence(evidence, "NOTIFICATION_"))
            write(zip, "behavior_classifications.csv", LayeredTrafficReportBuilder.behavior(behaviorRows))
            write(zip, "unexplained_uploads.csv", LayeredTrafficReportBuilder.behavior(
                behaviorRows.filter { it.classification == "UNEXPLAINED_BACKGROUND_UPLOAD" },
            ))
            write(zip, "snapshots.csv", buildString {
                appendLine("planned_at,actual_at,delay_ms,trigger_source,total_mobile_bytes,is_mobile,radio,signal_dbm,signal_level,roaming,traffic_collection_status,screen_interactive,device_locked,charging,light_idle,deep_idle,battery_exempt,physical_network_id,vpn_network_id,vpn_underlying_network_id,guardian_state,guardian_retry_attempt,capture_complete,last_network_lost_at,last_network_recovered_at")
                snapshots.forEach { appendLine("${it.plannedAtEpochMs},${it.timestampEpochMs},${it.delayMs},${csv(it.triggerSource)},${it.totalMobileBytes},${it.mobileDataActive},${csv(it.radioTechnology)},${it.signalDbm ?: ""},${it.signalLevel ?: ""},${it.roaming ?: ""},${csv(it.trafficCollectionStatus)},${it.screenInteractive},${it.deviceLocked},${it.charging},${it.lightIdle},${it.deepIdle},${it.batteryExempt},${csv(it.physicalNetworkId ?: "")},${csv(it.vpnNetworkId ?: "")},${csv(it.vpnUnderlyingNetworkId ?: "")},${csv(it.guardianState)},${it.guardianRetryAttempt},${it.captureComplete},${it.lastNetworkLostAtEpochMs ?: ""},${it.lastNetworkRecoveredAtEpochMs ?: ""}") }
            })
            write(zip, "probes.csv", ProbeCsvRenderer.render(probes))
            write(zip, "probe_attempts.csv", buildString { appendLine("timestamp,endpoint,attempt_number,dns_ms,https_ms,bytes,failure"); attempts.forEach { appendLine("${it.timestampEpochMs},${csv(it.endpoint)},${it.attemptNumber},${it.dnsLatencyMs ?: ""},${it.httpsLatencyMs ?: ""},${it.consumedBytes},${csv(it.failure ?: "")}") } })
            write(zip, "traffic.csv", buildString { appendLine("window_start,window_end,package_name,display_name,mobile_bytes,approximate"); traffic.forEach { appendLine("${it.windowStartEpochMs},${it.windowEndEpochMs},${csv(it.packageName)},${csv(it.displayName)},${it.mobileBytes},${it.approximate}") } })
            write(zip, "connection_flows.csv", buildString {
                appendLine("timestamp,protocol,destination_address,destination_port,endpoint_host,wire_bytes,owner_package,owner_label,activity_class,direction,stage,outcome,forwarding_reason,endpoint_category,classification_reason")
                val classifier = EndpointClassifier()
                flows.forEach {
                    val endpoint = classifier.classify(it.endpointHost)
                    appendLine("${it.timestampEpochMs},${csv(it.protocol)},${csv(it.destinationAddress)},${it.destinationPort},${csv(it.endpointHost ?: "")},${it.wireBytes},${csv(it.ownerPackage ?: "")},${csv(it.ownerLabel ?: "")},${csv(it.activityClass)},${csv(it.direction)},${csv(it.stage)},${csv(it.outcome)},${csv(it.reason ?: "")},${csv(endpoint.category.name)},${csv(endpoint.reason)}")
                }
            })
            write(zip, "app_evidence.csv", buildString { appendLine("timestamp,package_name,display_name,evidence_type,event_source,event_key,content_fingerprint,metadata_availability,payload_json"); evidence.forEach { appendLine("${it.timestampEpochMs},${csv(it.packageName)},${csv(it.displayName ?: "")},${csv(it.evidenceType)},${csv(it.source)},${csv(it.eventKey ?: "")},${csv(it.contentFingerprint ?: "")},${csv(it.metadataAvailability)},${csv(it.payloadJson)}") } })
            write(zip, "behavior_analysis.csv", LayeredTrafficReportBuilder.behavior(behaviorRows))
            write(zip, "diagnostic_events.csv", buildString { appendLine("timestamp,event_type,details"); events.forEach { appendLine("${it.timestampEpochMs},${csv(it.eventType)},${csv(it.details ?: "")}") } })
            val completeReport = CompleteReportFactory(database).build(
                run, snapshots, probes, attempts, traffic, flows, events, evidence, accounting, lifecycle, gaps,
            )
            zip.putNextEntry(ZipEntry("report.json"))
            CompleteReportJson.write(completeReport, OutputStreamWriter(zip, Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    companion object {
        val requiredSchemaV3Entries = setOf(
            "traffic_summary.csv", "system_mobile_traffic.csv", "tunnel_observed_traffic.csv",
            "forwarding_outcomes.csv", "probe_traffic.csv", "heartbeat_traffic.csv",
            "capture_gaps.csv", "network_lifecycle.csv", "endpoint_observations.csv",
            "media_events.csv", "notification_events.csv", "behavior_classifications.csv",
            "unexplained_uploads.csv", "report.html", "report.json",
        )
    }

    private fun write(zip: ZipOutputStream, name: String, content: String) { zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry() }
    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
    private data class AggregatedAppTraffic(val displayName: String, val mobileBytes: Long)
}
