package com.redmiklab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionStateTest {
    @Test
    fun requires_usage_access_before_app_traffic_collection() {
        assertEquals(
            PermissionState.MissingUsageAccess,
            PermissionState.from(notification = true, phone = true, usageAccess = false),
        )
    }
}
