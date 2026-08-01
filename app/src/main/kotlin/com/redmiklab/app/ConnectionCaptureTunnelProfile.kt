package com.redmiklab.app

object ConnectionCaptureTunnelProfile {
    val addresses: List<Pair<String, Int>> = listOf(
        "10.77.0.2" to 32,
        "fd42:7265:646d:6900::2" to 128,
    )
    val routes: List<Pair<String, Int>> = listOf(
        "0.0.0.0" to 0,
        "::" to 0,
    )

    fun dnsServers(carrierServers: List<String>): List<String> =
        carrierServers.filter(String::isNotBlank).distinct().ifEmpty {
            listOf("223.5.5.5", "2400:3200::1")
        }
}
