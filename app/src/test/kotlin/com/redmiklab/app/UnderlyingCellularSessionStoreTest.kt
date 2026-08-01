package com.redmiklab.app

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UnderlyingCellularSessionStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clearStore() {
        UnderlyingCellularSessionStore(context).clear()
    }

    @Test
    fun recorded_network_survives_a_new_store_instance() {
        UnderlyingCellularSessionStore(context).record("218")

        assertEquals("218", UnderlyingCellularSessionStore(context).read())
    }

    @Test
    fun clear_removes_the_session_network() {
        val store = UnderlyingCellularSessionStore(context)
        store.record("218")

        store.clear()

        assertNull(store.read())
    }
}
