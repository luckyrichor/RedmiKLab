package com.redmiklab.diagnostics

import java.time.Instant

data class TrafficWindow(val start: Instant, val end: Instant)

class TrafficWindowPlanner(runStartedAt: Instant) {
    private var previousWindowEnd = runStartedAt

    @Synchronized
    fun nextEndingAt(snapshotAt: Instant): TrafficWindow {
        require(!snapshotAt.isBefore(previousWindowEnd)) { "Snapshot cannot precede the previous traffic window" }
        return TrafficWindow(previousWindowEnd, snapshotAt).also { previousWindowEnd = snapshotAt }
    }
}
