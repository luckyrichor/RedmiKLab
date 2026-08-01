package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeForwarderContractTest {
    @Test
    fun declares_arm64_v8a_as_the_initial_supported_device_abi() {
        assertEquals(setOf("arm64-v8a"), NativeForwarderContract.initialSupportedAbis)
    }

    @Test
    fun marks_the_native_forwarder_unavailable_until_its_library_loads() {
        assertEquals(false, NativeForwarderContract.isLoaded())
    }
}
