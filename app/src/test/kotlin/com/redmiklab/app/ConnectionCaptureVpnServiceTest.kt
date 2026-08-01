package com.redmiklab.app

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectionCaptureVpnServiceTest {
    @Test
    fun rejects_an_invalid_socket_descriptor() {
        val service = Robolectric.buildService(ConnectionCaptureVpnService::class.java).create().get()

        assertFalse(service.protectSocket(-1))
        assertTrue(service is NativeSocketProtector)
        assertTrue(service is NativeConnectionObserver)
    }

    @Test
    fun destroying_the_service_clears_stale_underlying_network_identity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = UnderlyingCellularSessionStore(context)
        store.record("218")
        val controller = Robolectric.buildService(ConnectionCaptureVpnService::class.java).create()

        controller.destroy()

        assertNull(store.read())
    }
}
