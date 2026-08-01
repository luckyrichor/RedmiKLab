package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticPreferencesUpgradePolicyTest {
    @Test
    fun replaces_the_previous_builtin_endpoint_pair_on_upgrade() {
        assertEquals(
            EndpointPair(DiagnosticDefaults.PRIMARY, DiagnosticDefaults.FALLBACK),
            DiagnosticDefaults.upgradeLegacyPair(EndpointPair(DiagnosticDefaults.LEGACY_PRIMARY, DiagnosticDefaults.LEGACY_FALLBACK)),
        )
    }

    @Test
    fun preserves_a_user_custom_endpoint_pair_on_upgrade() {
        val custom = EndpointPair("https://example.cn/custom", DiagnosticDefaults.LEGACY_FALLBACK)
        assertEquals(custom, DiagnosticDefaults.upgradeLegacyPair(custom))
    }
}
