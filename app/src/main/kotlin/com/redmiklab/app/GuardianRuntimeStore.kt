package com.redmiklab.app

import android.content.Context

data class GuardianRuntimeSnapshot(
    val phase: String,
    val generation: Long,
    val physicalNetworkId: String?,
    val vpnUnderlyingNetworkId: String?,
    val retryAttempt: Int,
    val captureComplete: Boolean,
    val lastNetworkLostAtEpochMs: Long?,
    val lastNetworkRecoveredAtEpochMs: Long?,
    val lastReconnectActionType: String?,
    val lastReconnectActionAtEpochMs: Long?,
    val lastReconnectActionDetails: String?,
)

class GuardianRuntimeStore(context: Context) {
    private val store = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun record(
        state: GuardianState,
        vpnUnderlyingNetworkId: String?,
        captureComplete: Boolean,
        recordLostNow: Boolean = false,
        recordRecoveredNow: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        store.edit()
            .putString(KEY_PHASE, state.phase.name)
            .putLong(KEY_GENERATION, state.generation)
            .putString(KEY_PHYSICAL_NETWORK_ID, state.physicalNetworkId)
            .putString(KEY_VPN_UNDERLYING_NETWORK_ID, vpnUnderlyingNetworkId)
            .putInt(KEY_RETRY_ATTEMPT, state.retryAttempt)
            .putBoolean(KEY_CAPTURE_COMPLETE, captureComplete)
            .apply {
                if (recordLostNow) putLong(KEY_LAST_LOST_AT, now)
                if (recordRecoveredNow) putLong(KEY_LAST_RECOVERED_AT, now)
            }
            .apply()
    }

    fun read() = GuardianRuntimeSnapshot(
        phase = store.getString(KEY_PHASE, "UNKNOWN") ?: "UNKNOWN",
        generation = store.getLong(KEY_GENERATION, 0L),
        physicalNetworkId = store.getString(KEY_PHYSICAL_NETWORK_ID, null),
        vpnUnderlyingNetworkId = store.getString(KEY_VPN_UNDERLYING_NETWORK_ID, null),
        retryAttempt = store.getInt(KEY_RETRY_ATTEMPT, 0),
        captureComplete = store.getBoolean(KEY_CAPTURE_COMPLETE, false),
        lastNetworkLostAtEpochMs = store.longOrNull(KEY_LAST_LOST_AT),
        lastNetworkRecoveredAtEpochMs = store.longOrNull(KEY_LAST_RECOVERED_AT),
        lastReconnectActionType = store.getString(KEY_LAST_RECONNECT_ACTION_TYPE, null),
        lastReconnectActionAtEpochMs = store.longOrNull(KEY_LAST_RECONNECT_ACTION_AT),
        lastReconnectActionDetails = store.getString(KEY_LAST_RECONNECT_ACTION_DETAILS, null),
    )

    fun recordAction(event: ReconnectActionEvent, timestampEpochMs: Long = System.currentTimeMillis()) {
        store.edit()
            .putString(KEY_LAST_RECONNECT_ACTION_TYPE, event.type.name)
            .putLong(KEY_LAST_RECONNECT_ACTION_AT, timestampEpochMs)
            .putString(
                KEY_LAST_RECONNECT_ACTION_DETAILS,
                "generation=${event.generation};attempt=${event.attempt};${event.details}",
            )
            .apply()
    }

    private fun android.content.SharedPreferences.longOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0) else null

    private companion object {
        const val PREFERENCES = "guardian_runtime"
        const val KEY_PHASE = "phase"
        const val KEY_GENERATION = "generation"
        const val KEY_PHYSICAL_NETWORK_ID = "physical_network_id"
        const val KEY_VPN_UNDERLYING_NETWORK_ID = "vpn_underlying_network_id"
        const val KEY_RETRY_ATTEMPT = "retry_attempt"
        const val KEY_CAPTURE_COMPLETE = "capture_complete"
        const val KEY_LAST_LOST_AT = "last_lost_at"
        const val KEY_LAST_RECOVERED_AT = "last_recovered_at"
        const val KEY_LAST_RECONNECT_ACTION_TYPE = "last_reconnect_action_type"
        const val KEY_LAST_RECONNECT_ACTION_AT = "last_reconnect_action_at"
        const val KEY_LAST_RECONNECT_ACTION_DETAILS = "last_reconnect_action_details"
    }
}
