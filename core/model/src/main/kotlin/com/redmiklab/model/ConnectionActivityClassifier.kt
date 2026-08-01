package com.redmiklab.model

enum class ConnectionActivityClass {
    ForegroundActive,
    BackgroundScreenOn,
    BackgroundScreenLocked,
    Unattributed,
}

object ConnectionActivityClassifier {
    fun classify(screenLocked: Boolean, foregroundPackage: String?, ownerPackage: String?): ConnectionActivityClass = when {
        ownerPackage.isNullOrBlank() -> ConnectionActivityClass.Unattributed
        screenLocked -> ConnectionActivityClass.BackgroundScreenLocked
        foregroundPackage == ownerPackage -> ConnectionActivityClass.ForegroundActive
        else -> ConnectionActivityClass.BackgroundScreenOn
    }
}
