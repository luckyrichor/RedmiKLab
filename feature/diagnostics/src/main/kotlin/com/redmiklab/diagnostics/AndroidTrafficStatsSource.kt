package com.redmiklab.diagnostics

import android.app.usage.NetworkStatsManager
import android.app.usage.NetworkStats
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import com.redmiklab.model.AppTraffic
import com.redmiklab.model.TrafficWindow
import java.time.Instant

class AndroidTrafficStatsSource(context: Context) : TrafficStatsSource {
    private val networkStats = context.getSystemService(NetworkStatsManager::class.java)
    private val packages = context.packageManager

    override fun readWindow(start: Instant, end: Instant): TrafficWindow {
        val uidBuckets = networkStats.querySummary(
            ConnectivityManager.TYPE_MOBILE,
            null,
            start.toEpochMilli(),
            end.toEpochMilli(),
        ).use { stats ->
            buildList {
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val bytes = bucket.rxBytes + bucket.txBytes
                    if (bytes > 0 && bucket.uid >= 0) add(UidTraffic(bucket.uid, bytes))
                }
            }
        }
        val appUsage = UidTrafficAggregator.aggregate(uidBuckets).map { appTraffic(it.uid, it.bytes) }

        return TrafficWindow(
            start = start,
            end = end,
            totalMobileBytes = appUsage.sumOf { it.mobileBytes },
            appUsage = appUsage,
            isApproximate = true,
        )
    }

    private fun appTraffic(uid: Int, bytes: Long): AppTraffic {
        val packageName = packages.getPackagesForUid(uid)?.firstOrNull() ?: "uid:$uid"
        val label = runCatching {
            packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        return AppTraffic(packageName, label, bytes)
    }
}
