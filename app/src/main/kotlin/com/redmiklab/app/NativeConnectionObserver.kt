package com.redmiklab.app

/** Metadata-only callback. Packet payload is never passed across this boundary. */
interface NativeConnectionObserver {
    fun onForwardingObservation(
        protocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
        direction: String,
        stage: String,
        outcome: String,
        wireBytes: Long,
        reason: String?,
        hostname: String?,
    )
}
