package com.redmiklab.diagnostics

import java.time.Instant

class SnapshotSchedule(intervalSeconds: Long) {
    private val intervalMillis = intervalSeconds * 1_000

    fun nextPeriodicAfter(snapshotAt: Instant): Instant = snapshotAt.plusMillis(intervalMillis)

    fun shouldSchedulePeriodic(nextSnapshotAt: Instant, end: Instant): Boolean = nextSnapshotAt.isBefore(end)

    fun finalSnapshotAt(end: Instant): Instant = end
}
