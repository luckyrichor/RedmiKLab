package com.redmiklab.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

data class DeviceRuntimeState(
    val screenInteractive: Boolean,
    val deviceLocked: Boolean,
    val charging: Boolean,
    val lightIdle: Boolean,
    val deepIdle: Boolean,
    val batteryExempt: Boolean,
    val physicalNetworkId: String?,
    val vpnNetworkId: String?,
    val vpnUnderlyingNetworkId: String?,
    val guardianState: String,
    val guardianRetryAttempt: Int,
    val captureComplete: Boolean,
    val lastNetworkLostAtEpochMs: Long?,
    val lastNetworkRecoveredAtEpochMs: Long?,
) {
    companion object {
        fun unknown() = DeviceRuntimeState(
            false, false, false, false, false, false,
            null, null, null, "UNKNOWN", 0, false, null, null,
        )
    }
}

fun interface DeviceRuntimeStateSource {
    fun read(): DeviceRuntimeState
}

class DeviceIdleSnapshotSource(context: Context) : DeviceRuntimeStateSource {
    private val appContext = context.applicationContext
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val guardianStore = GuardianRuntimeStore(context)

    @Suppress("DEPRECATION")
    override fun read(): DeviceRuntimeState {
        val guardian = guardianStore.read()
        val networks = connectivity.allNetworks.toList()
        val physical = PhysicalCellularNetworkSelector.select(
            networks.mapNotNull { network ->
                val capabilities = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
                PhysicalCellularNetworkCandidate(
                    network.toString(),
                    network,
                    AndroidCellularCandidateFactsFactory.create(
                        capabilities,
                        network.toString() == guardian.vpnUnderlyingNetworkId,
                    ),
                )
            },
            activeDefaultIsValidatedVpn = connectivity.activeNetwork
                ?.let(connectivity::getNetworkCapabilities)
                ?.let { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }
                ?: false,
        ).selected?.networkId
        val vpn = networks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }?.toString()
        val battery = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return DeviceRuntimeState(
            screenInteractive = power.isInteractive,
            deviceLocked = keyguard.isDeviceLocked,
            charging = charging,
            lightIdle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                power.isDeviceLightIdleMode,
            deepIdle = power.isDeviceIdleMode,
            batteryExempt = power.isIgnoringBatteryOptimizations(appContext.packageName),
            physicalNetworkId = physical ?: guardian.physicalNetworkId,
            vpnNetworkId = vpn,
            vpnUnderlyingNetworkId = guardian.vpnUnderlyingNetworkId,
            guardianState = guardian.phase,
            guardianRetryAttempt = guardian.retryAttempt,
            captureComplete = guardian.captureComplete,
            lastNetworkLostAtEpochMs = guardian.lastNetworkLostAtEpochMs,
            lastNetworkRecoveredAtEpochMs = guardian.lastNetworkRecoveredAtEpochMs,
        )
    }
}
