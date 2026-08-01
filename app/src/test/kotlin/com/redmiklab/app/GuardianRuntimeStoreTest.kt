package com.redmiklab.app

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GuardianRuntimeStoreTest {
    @Test
    fun records_guardian_generation_for_delayed_action_validation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = GuardianRuntimeStore(context)
        val state = GuardianState.initial().copy(
            phase = GuardianPhase.RECONNECTING,
            generation = 27,
            retryAttempt = 4,
        )

        store.record(state, vpnUnderlyingNetworkId = null, captureComplete = false)

        assertEquals(27L, store.read().generation)
    }

    @Test
    fun records_the_latest_screen_wake_or_retry_action_even_before_a_run_is_attached() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = GuardianRuntimeStore(context)

        store.recordAction(
            ReconnectActionEvent(
                ReconnectActionType.SCREEN_WAKE_REQUESTED,
                generation = 12,
                attempt = 4,
                details = "accepted=true;scheduled",
            ),
            timestampEpochMs = 123_456L,
        )

        val snapshot = store.read()
        assertEquals("SCREEN_WAKE_REQUESTED", snapshot.lastReconnectActionType)
        assertEquals(123_456L, snapshot.lastReconnectActionAtEpochMs)
        assertTrue(snapshot.lastReconnectActionDetails!!.contains("attempt=4"))
        assertTrue(snapshot.lastReconnectActionDetails!!.contains("generation=12"))
    }
}
