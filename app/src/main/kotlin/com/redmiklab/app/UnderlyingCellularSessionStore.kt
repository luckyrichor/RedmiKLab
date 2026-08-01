package com.redmiklab.app

import android.content.Context

class UnderlyingCellularSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        ConnectionCaptureVpnService.PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun read(): String? = preferences.getString(KEY, null)

    fun record(networkId: String) {
        preferences.edit().putString(KEY, networkId).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "underlying_cellular_network_id"
    }
}
