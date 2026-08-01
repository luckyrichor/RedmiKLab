package com.redmiklab.diagnostics

enum class PermissionState {
    Ready,
    MissingNotifications,
    MissingPhoneState,
    MissingUsageAccess;

    companion object {
        fun from(notification: Boolean, phone: Boolean, usageAccess: Boolean): PermissionState = when {
            !notification -> MissingNotifications
            !phone -> MissingPhoneState
            !usageAccess -> MissingUsageAccess
            else -> Ready
        }
    }
}
