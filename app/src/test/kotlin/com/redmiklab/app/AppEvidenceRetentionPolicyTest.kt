package com.redmiklab.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppEvidenceRetentionPolicyTest {
    private val policy = AppEvidenceRetentionPolicy()

    @Test
    fun keeps_full_notifications_only_for_network_or_media_participants() {
        assertTrue(policy.keep("NOTIFICATION_POSTED", "com.video", setOf("com.video"), emptySet()))
        assertTrue(policy.keep("NOTIFICATION_POSTED", "com.music", emptySet(), setOf("com.music")))
        assertFalse(policy.keep("NOTIFICATION_POSTED", "com.unrelated", setOf("com.video"), setOf("com.music")))
    }

    @Test
    fun keeps_media_and_minimal_foreground_context_evidence() {
        assertTrue(policy.keep("MEDIA_SESSION", "com.cached.video", emptySet(), setOf("com.cached.video")))
        assertTrue(policy.keep("FOREGROUND_CONTEXT", "com.reader", emptySet(), emptySet()))
    }
}
