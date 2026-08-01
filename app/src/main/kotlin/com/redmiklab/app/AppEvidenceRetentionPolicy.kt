package com.redmiklab.app

class AppEvidenceRetentionPolicy {
    fun keep(
        evidenceType: String,
        packageName: String,
        networkParticipants: Set<String>,
        mediaParticipants: Set<String>,
    ): Boolean = when {
        evidenceType == "FOREGROUND_CONTEXT" -> true
        evidenceType.startsWith("MEDIA_") -> packageName in mediaParticipants
        evidenceType.startsWith("NOTIFICATION_") ->
            packageName in networkParticipants || packageName in mediaParticipants
        else -> false
    }
}
