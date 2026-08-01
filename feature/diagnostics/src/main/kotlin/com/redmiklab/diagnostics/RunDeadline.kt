package com.redmiklab.diagnostics

import java.time.Instant

class RunDeadline(val end: Instant) {
    fun allows(now: Instant): Boolean = now.isBefore(end)
}
