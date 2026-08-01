package com.redmiklab.diagnostics

import com.redmiklab.model.RadioTechnology

object RadioTechnologyMapper {
    fun fromNetworkType(networkType: Int): RadioTechnology = when (networkType) {
        20 -> RadioTechnology.FiveG // TelephonyManager.NETWORK_TYPE_NR
        13 -> RadioTechnology.FourG // TelephonyManager.NETWORK_TYPE_LTE
        else -> RadioTechnology.Other
    }
}
