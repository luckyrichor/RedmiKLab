package com.redmiklab.app

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import com.redmiklab.diagnostics.CellularNetworkCandidateDecision
import com.redmiklab.diagnostics.CellularNetworkCandidatePolicy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AndroidCellularNetworkResolver(
    private val connectivity: ConnectivityManager,
    private val recordedUnderlyingNetworkId: String? = null,
) {
    data class Resolution(
        val network: Network?,
        val diagnostic: CellularNetworkDiagnostic,
    )

    @Suppress("DEPRECATION")
    fun current(): Network? = selectCurrent().selected?.value

    fun resolve(timeoutMs: Long): Resolution {
        val startedAtElapsedMs = SystemClock.elapsedRealtime()
        val initial = systemState()
        current()?.let { network ->
            return resolution(network, CellularResolutionOutcome.CURRENT_CANDIDATE, initial, startedAtElapsedMs)
        }
        val resolved = AtomicReference<Network?>()
        val ready = CountDownLatch(1)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = accept(network)

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = accept(network)

            override fun onUnavailable() {
                ready.countDown()
            }

            private fun accept(network: Network) {
                if (candidateScore(network) != null && resolved.compareAndSet(null, network)) ready.countDown()
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        var registered = false
        return try {
            connectivity.requestNetwork(request, callback)
            registered = true
            ready.await(timeoutMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            val network = resolved.get() ?: current()
            val outcome = when {
                network != null -> CellularResolutionOutcome.REQUEST_RESOLVED
                else -> CellularResolutionOutcome.NO_SAFE_PHYSICAL_CELLULAR
            }
            resolution(network, outcome, initial, startedAtElapsedMs)
        } catch (error: SecurityException) {
            resolution(current(), CellularResolutionOutcome.REQUEST_SECURITY_ERROR, initial, startedAtElapsedMs, error)
        } catch (error: RuntimeException) {
            resolution(current(), CellularResolutionOutcome.REQUEST_RUNTIME_ERROR, initial, startedAtElapsedMs, error)
        } finally {
            if (registered) runCatching { connectivity.unregisterNetworkCallback(callback) }
        }
    }

    fun await(timeoutMs: Long): Network? = resolve(timeoutMs).network

    private fun resolution(
        network: Network?,
        outcome: CellularResolutionOutcome,
        initial: CellularSystemNetworkState,
        startedAtElapsedMs: Long,
        error: Throwable? = null,
    ) = Resolution(
        network,
        CellularNetworkDiagnostic(
            outcome = outcome,
            selectedNetworkId = network?.toString(),
            initialState = initial,
            finalState = systemState(),
            resolutionWaitMs = (SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0),
            errorType = error?.javaClass?.simpleName,
        ),
    )

    @Suppress("DEPRECATION")
    private fun systemState(): CellularSystemNetworkState {
        val active = connectivity.activeNetwork
        return CellularSystemNetworkState(
            activeNetworkId = active?.toString(),
            activeNetworkMetered = connectivity.isActiveNetworkMetered,
            restrictBackgroundStatus = connectivity.restrictBackgroundStatus,
            networks = connectivity.allNetworks.mapNotNull { network -> snapshot(network, network == active) },
            recordedUnderlyingNetworkId = recordedUnderlyingNetworkId,
        )
    }

    private fun snapshot(network: Network, isActive: Boolean): CellularNetworkCandidateSnapshot? {
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        val link = connectivity.getLinkProperties(network)
        val decision = candidateDecision(network, capabilities)
        return CellularNetworkCandidateSnapshot(
            networkId = network.toString(),
            isActive = isActive,
            transports = buildList {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("CELLULAR")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WIFI")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("BLUETOOTH")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ETHERNET")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) add("WIFI_AWARE")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)) add("LOWPAN")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB)
                ) add("USB")
                if (isEmpty()) add("OTHER")
            },
            hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            isNotSuspended = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED),
            isNotRestricted = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED),
            isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            isRecordedUnderlying = network.toString() == recordedUnderlyingNetworkId,
            score = decision.score,
            rejectionReason = decision.rejection?.name,
            interfaceName = link?.interfaceName,
            dnsServers = link?.dnsServers.orEmpty().mapNotNull { it.hostAddress },
        )
    }

    private fun candidateScore(network: Network): Int? {
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        return candidateDecision(network, capabilities).score
    }

    @Suppress("DEPRECATION")
    private fun selectCurrent(): PhysicalCellularNetworkSelection<Network> =
        PhysicalCellularNetworkSelector.select(
            connectivity.allNetworks.mapNotNull { network ->
                val capabilities = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
                PhysicalCellularNetworkCandidate(
                    networkId = network.toString(),
                    value = network,
                    facts = AndroidCellularCandidateFactsFactory.create(
                        capabilities,
                        isRecordedUnderlying = network.toString() == recordedUnderlyingNetworkId,
                    ),
                )
            },
            activeDefaultIsValidatedVpn(),
        )

    private fun candidateDecision(
        network: Network,
        capabilities: NetworkCapabilities,
    ): CellularNetworkCandidateDecision = CellularNetworkCandidatePolicy.evaluate(
        AndroidCellularCandidateFactsFactory.create(
            capabilities,
            isRecordedUnderlying = network.toString() == recordedUnderlyingNetworkId,
        ),
        activeDefaultIsValidatedVpn(),
    )

    private fun activeDefaultIsValidatedVpn(): Boolean {
        val activeCapabilities = connectivity.activeNetwork
            ?.let(connectivity::getNetworkCapabilities)
            ?: return false
        return activeCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            activeCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
