package com.redmiklab.diagnostics

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import com.redmiklab.model.NetworkSnapshot
import com.redmiklab.model.RadioTechnology
import java.time.Instant

class AndroidNetworkSnapshotSource(context: Context) : NetworkSnapshotSource {
    private val applicationContext = context.applicationContext
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override fun read(): NetworkSnapshot {
        val isMobile = connectivity.allNetworks.any { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@any false
            CellularNetworkCandidatePolicy.evaluate(
                facts = CellularNetworkCandidateFacts(
                    isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                    isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                    isIms = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS),
                    isEims = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_EIMS),
                    isMms = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS),
                    hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    isNotSuspended = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED),
                    isRecordedUnderlying = false,
                ),
                activeDefaultIsValidatedVpn = false,
            ).score != null
        }
        val hasPhonePermission = applicationContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val networkType = if (hasPhonePermission) runCatching { telephony.dataNetworkType }.getOrDefault(0) else 0
        val signal = if (hasPhonePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { telephony.signalStrength }.getOrNull()
        } else null
        val signalDbm = when {
            signal == null -> null
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> signal.cellSignalStrengths.maxOfOrNull { it.dbm }
            else -> null
        }

        return NetworkSnapshot(
            timestamp = Instant.now(),
            isMobileDataActive = isMobile,
            radioTechnology = if (isMobile) RadioTechnologyMapper.fromNetworkType(networkType) else RadioTechnology.Unavailable,
            signalDbm = signalDbm,
            signalLevel = signal?.level,
            isRoaming = runCatching { telephony.isNetworkRoaming }.getOrNull(),
        )
    }
}
