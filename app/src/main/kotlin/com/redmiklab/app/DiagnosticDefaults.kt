package com.redmiklab.app

data class EndpointPair(val primary: String, val fallback: String)

object DiagnosticDefaults {
    const val LEGACY_PRIMARY = "https://speed.cloudflare.com/__down?bytes=5000000"
    const val LEGACY_FALLBACK = "https://download.thinkbroadband.com/5MB.zip"
    const val PRIMARY = "https://repo.huaweicloud.com/centos/7/isos/x86_64/CentOS-7-x86_64-DVD-2009.iso"
    const val FALLBACK = "https://mirrors.ustc.edu.cn/ubuntu-releases/24.04.4/ubuntu-24.04.4-desktop-amd64.iso"

    fun upgradeLegacyPair(pair: EndpointPair): EndpointPair =
        if (pair == EndpointPair(LEGACY_PRIMARY, LEGACY_FALLBACK)) EndpointPair(PRIMARY, FALLBACK) else pair
}
