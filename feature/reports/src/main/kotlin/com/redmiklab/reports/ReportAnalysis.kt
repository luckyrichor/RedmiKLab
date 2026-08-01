package com.redmiklab.reports

data class TrafficSlice(val label: String, val bytes: Long)

data class ReportAnalysis(
    val averageSignalDbm: Double?,
    val signalSamples: List<Pair<Long, Int>>,
    val topApps: List<TrafficSlice>,
    val findings: List<String>,
)

object ReportAnalyzer {
    fun analyze(report: CompleteReport): ReportAnalysis {
        val snapshots = report.tables["snapshots"].orEmpty()
        val signal = snapshots.mapNotNull { row ->
            val time = row["timestampEpochMs"]?.toLongOrNull() ?: return@mapNotNull null
            val dbm = row["signalDbm"]?.toIntOrNull() ?: return@mapNotNull null
            time to dbm
        }
        val topApps = report.tables["appTraffic"].orEmpty()
            .groupBy { it["displayName"].orEmpty().ifBlank { it["packageName"].orEmpty().ifBlank { "未知应用" } } }
            .map { (label, rows) -> TrafficSlice(label, rows.sumOf { it["mobileBytes"]?.toLongOrNull() ?: 0 }) }
            .sortedByDescending { it.bytes }
        val findings = buildList {
            if (snapshots.any { it["mobileDataActive"] == "false" }) {
                add("部分快照显示移动数据不可用；请结合网络生命周期与主动测速记录判断持续时间。")
            }
            if (signal.isEmpty()) add("报告没有可用的信号强度样本。")
            else if (signal.map { it.second }.average() <= -100) add("平均信号偏弱，可能影响吞吐和交互稳定性。")
            if (topApps.isNotEmpty()) {
                add("系统时段级近似统计中流量最高的应用为 ${topApps.first().label}；该关联不能单独证明具体行为。")
            }
            if (report.summary.probeCount == 0) add("没有主动测速结果，无法评价主动下载吞吐。")
        }
        return ReportAnalysis(
            averageSignalDbm = signal.map { it.second }.average().takeIf { !it.isNaN() },
            signalSamples = signal,
            topApps = topApps,
            findings = findings,
        )
    }
}

data class ReportComparisonResult(
    val reportA: ReportRun,
    val reportB: ReportRun,
    val downloadMbpsDelta: Double?,
    val snapshotCountDelta: Int,
    val systemMobileBytesDelta: Long,
    val notice: String,
)

object ReportComparator {
    fun compare(a: CompleteReport, b: CompleteReport): ReportComparisonResult =
        ReportComparisonResult(
            reportA = a.run,
            reportB = b.run,
            downloadMbpsDelta = if (a.summary.averageDownloadMbps != null && b.summary.averageDownloadMbps != null) {
                a.summary.averageDownloadMbps - b.summary.averageDownloadMbps
            } else null,
            snapshotCountDelta = a.summary.snapshotCount - b.summary.snapshotCount,
            systemMobileBytesDelta = a.summary.systemMobileBytes - b.summary.systemMobileBytes,
            notice = "这是两份独立报告的关联比较，不能单独证明运营商、网络或某个应用造成了差异。",
        )
}
