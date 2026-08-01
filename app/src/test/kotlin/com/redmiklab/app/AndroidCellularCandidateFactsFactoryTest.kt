package com.redmiklab.app

import android.net.NetworkCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidCellularCandidateFactsFactoryTest {
    @Test
    fun maps_inherited_cellular_vpn_and_validation_capabilities() {
        val capabilities = capabilities(
            transports = intArrayOf(
                NetworkCapabilities.TRANSPORT_CELLULAR,
                NetworkCapabilities.TRANSPORT_VPN,
            ),
            capabilities = intArrayOf(
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED,
            ),
        )

        val facts = AndroidCellularCandidateFactsFactory.create(capabilities, isRecordedUnderlying = false)

        assertTrue(facts.isCellular)
        assertTrue(facts.isVpn)
        assertTrue(facts.hasInternet)
        assertTrue(facts.isValidated)
        assertTrue(facts.isNotSuspended)
        assertFalse(facts.isRecordedUnderlying)
    }

    @Test
    fun maps_each_carrier_specialized_capability() {
        fun factsFor(capability: Int) = AndroidCellularCandidateFactsFactory.create(
            capabilities(
                transports = intArrayOf(NetworkCapabilities.TRANSPORT_CELLULAR),
                capabilities = intArrayOf(capability),
            ),
            isRecordedUnderlying = true,
        )

        assertTrue(factsFor(NetworkCapabilities.NET_CAPABILITY_IMS).isIms)
        assertTrue(factsFor(NetworkCapabilities.NET_CAPABILITY_EIMS).isEims)
        assertTrue(factsFor(NetworkCapabilities.NET_CAPABILITY_MMS).isMms)
    }

    private fun capabilities(transports: IntArray, capabilities: IntArray): NetworkCapabilities {
        val networkCapabilities = ShadowNetworkCapabilities.newInstance()
        val shadow = org.robolectric.Shadows.shadowOf(networkCapabilities)
        transports.forEach(shadow::addTransportType)
        capabilities.forEach(shadow::addCapability)
        return networkCapabilities
    }
}
