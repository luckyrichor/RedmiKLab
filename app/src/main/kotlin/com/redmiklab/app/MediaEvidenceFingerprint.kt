package com.redmiklab.app

import java.security.MessageDigest

object MediaEvidenceFingerprint {
    fun sha256(packageName: String, eventType: String, eventKey: String?, normalizedPayload: String): String {
        val value = listOf(packageName, eventType, eventKey.orEmpty(), normalizedPayload)
            .joinToString("\u0000")
            .toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(value)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
