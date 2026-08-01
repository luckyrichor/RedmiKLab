package com.redmiklab.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

interface PhysicalCellularNetworkListener {
    fun currentGeneration(): Long
    fun onPhysicalNetworkAvailable(generation: Long, network: Network)
    fun onPhysicalNetworkLost(network: Network)
    fun onPhysicalNetworkRequestUnavailable(generation: Long)
}

class PhysicalCellularNetworkMonitor(
    context: Context,
    private val listener: PhysicalCellularNetworkListener,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    private var passiveCallback: ConnectivityManager.NetworkCallback? = null
    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    fun startPassive() {
        if (passiveCallback != null) return
        val callback = callback { listener.currentGeneration() }
        passiveCallback = callback
        connectivity.registerNetworkCallback(request, callback)
    }

    fun requestNow(generation: Long) {
        activeCallback?.let(::unregisterSafely)
        val callback = callback { generation }
        activeCallback = callback
        connectivity.requestNetwork(request, callback, REQUEST_TIMEOUT_MS)
    }

    fun stop() {
        activeCallback?.let(::unregisterSafely)
        passiveCallback?.let(::unregisterSafely)
        activeCallback = null
        passiveCallback = null
    }

    private fun callback(generation: () -> Long) = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            connectivity.getNetworkCapabilities(network)?.let { capabilities ->
                dispatchIfPhysical(network, capabilities, generation())
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            dispatchIfPhysical(network, capabilities, generation())
        }

        override fun onLost(network: Network) {
            listener.onPhysicalNetworkLost(network)
        }

        override fun onUnavailable() {
            listener.onPhysicalNetworkRequestUnavailable(generation())
        }
    }

    private fun dispatchIfPhysical(
        network: Network,
        capabilities: NetworkCapabilities,
        generation: Long,
    ) {
        val candidate = PhysicalCellularNetworkCandidate(
            networkId = network.toString(),
            value = network,
            facts = AndroidCellularCandidateFactsFactory.create(
                capabilities,
                isRecordedUnderlying = false,
            ),
        )
        if (PhysicalCellularNetworkSelector.select(
                listOf(candidate),
                activeDefaultIsValidatedVpn = activeDefaultIsValidatedVpn(),
            ).selected != null
        ) listener.onPhysicalNetworkAvailable(generation, network)
    }

    private fun activeDefaultIsValidatedVpn(): Boolean {
        val active = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(active) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun unregisterSafely(callback: ConnectivityManager.NetworkCallback) {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 20_000
    }
}
