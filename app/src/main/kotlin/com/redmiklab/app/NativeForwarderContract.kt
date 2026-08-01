package com.redmiklab.app

/** Kotlin boundary for the direct connection forwarder. The VPN service owns its lifecycle. */
object NativeForwarderContract {
    val initialSupportedAbis: Set<String> = setOf("arm64-v8a")

    private val loaded = runCatching { System.loadLibrary("direct_forwarder") }.isSuccess

    fun isLoaded(): Boolean = loaded

    external fun nativeEngineVersion(): String
    external fun nativeProtectSocket(protector: NativeSocketProtector, fileDescriptor: Int): Boolean
    external fun nativeStart(
        tunnelFileDescriptor: Int,
        protector: NativeSocketProtector,
        observer: NativeConnectionObserver,
    ): Long
    external fun nativeStop(handle: Long)
}
