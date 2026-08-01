package com.redmiklab.diagnostics

data class UidTraffic(val uid: Int, val bytes: Long)

object UidTrafficAggregator {
    fun aggregate(buckets: List<UidTraffic>): List<UidTraffic> = buckets
        .groupingBy { it.uid }
        .fold(0L) { total, bucket -> total + bucket.bytes }
        .map { (uid, bytes) -> UidTraffic(uid, bytes) }
        .sortedBy { it.uid }
}
