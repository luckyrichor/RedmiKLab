package com.redmiklab.app

import android.net.NetworkCapabilities
import android.os.Build
import com.redmiklab.diagnostics.CellularNetworkCandidateFacts

object AndroidCellularCandidateFactsFactory {
    fun create(
        capabilities: NetworkCapabilities,
        isRecordedUnderlying: Boolean,
    ) = CellularNetworkCandidateFacts(
        isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        isIms = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS),
        isEims = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_EIMS),
        isMms = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS),
        hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        isNotSuspended = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED),
        isRecordedUnderlying = isRecordedUnderlying,
    )
}
