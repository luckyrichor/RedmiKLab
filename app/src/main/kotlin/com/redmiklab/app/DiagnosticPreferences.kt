package com.redmiklab.app

import android.content.Context
import com.redmiklab.model.DiagnosticConfig
import com.redmiklab.model.DiagnosticRuntimeMode
import java.time.LocalTime

class DiagnosticPreferences(context: Context) {
    private val store = context.getSharedPreferences("diagnostic_config", Context.MODE_PRIVATE)

    fun load(): DiagnosticConfig {
        upgradeLegacyProbeDefaults()
        return DiagnosticConfig.default().copy(
        start = LocalTime.of(store.getInt("start_hour", DiagnosticConfig.default().start.hour), store.getInt("start_minute", DiagnosticConfig.default().start.minute)),
        end = LocalTime.of(store.getInt("end_hour", DiagnosticConfig.default().end.hour), store.getInt("end_minute", DiagnosticConfig.default().end.minute)),
        probeEndpoint = store.getString("probe_endpoint", DiagnosticConfig.default().probeEndpoint) ?: DiagnosticConfig.default().probeEndpoint,
        fallbackProbeEndpoint = store.getString("fallback_probe_endpoint", DiagnosticConfig.default().fallbackProbeEndpoint)
            ?: DiagnosticConfig.default().fallbackProbeEndpoint,
        probeRetryDelaySeconds = store.getInt("probe_retry_delay_seconds", DiagnosticConfig.default().probeRetryDelaySeconds),
        snapshotMinutes = store.getInt("snapshot_minutes", DiagnosticConfig.default().snapshotMinutes),
        connectivityMinutes = store.getInt("connectivity_minutes", DiagnosticConfig.default().connectivityMinutes),
        throughputMinutes = store.getInt("throughput_minutes", DiagnosticConfig.default().throughputMinutes),
        runtimeMode = runCatching {
            DiagnosticRuntimeMode.valueOf(
                store.getString("runtime_mode", DiagnosticConfig.default().runtimeMode.name)
                    ?: DiagnosticConfig.default().runtimeMode.name,
            )
        }.getOrDefault(DiagnosticConfig.default().runtimeMode),
        )
    }

    private fun upgradeLegacyProbeDefaults() {
        if (store.getBoolean("probe_defaults_upgraded_v2", false)) return
        val current = EndpointPair(
            store.getString("probe_endpoint", DiagnosticConfig.default().probeEndpoint) ?: DiagnosticConfig.default().probeEndpoint,
            store.getString("fallback_probe_endpoint", DiagnosticConfig.default().fallbackProbeEndpoint)
                ?: DiagnosticConfig.default().fallbackProbeEndpoint,
        )
        val upgraded = DiagnosticDefaults.upgradeLegacyPair(current)
        store.edit()
            .putString("probe_endpoint", upgraded.primary)
            .putString("fallback_probe_endpoint", upgraded.fallback)
            .putBoolean("probe_defaults_upgraded_v2", true)
            .apply()
    }

    fun save(config: DiagnosticConfig) {
        store.edit()
            .putInt("start_hour", config.start.hour).putInt("start_minute", config.start.minute)
            .putInt("end_hour", config.end.hour).putInt("end_minute", config.end.minute)
            .putString("probe_endpoint", config.probeEndpoint)
            .putString("fallback_probe_endpoint", config.fallbackProbeEndpoint)
            .putInt("probe_retry_delay_seconds", config.probeRetryDelaySeconds)
            .putInt("snapshot_minutes", config.snapshotMinutes)
            .putInt("connectivity_minutes", config.connectivityMinutes)
            .putInt("throughput_minutes", config.throughputMinutes)
            .putString("runtime_mode", config.runtimeMode.name)
            .apply()
    }

    fun saveStartTime(start: LocalTime) {
        store.edit()
            .putInt("start_hour", start.hour)
            .putInt("start_minute", start.minute)
            .putBoolean("start_time_was_confirmed", true)
            .apply()
    }

    fun saveEndTime(end: LocalTime) {
        store.edit()
            .putInt("end_hour", end.hour)
            .putInt("end_minute", end.minute)
            .putBoolean("end_time_was_confirmed", true)
            .apply()
    }

    fun markStartTimeOpened() {
        store.edit().putBoolean("start_time_was_confirmed", true).apply()
    }

    fun markEndTimeOpened() {
        store.edit().putBoolean("end_time_was_confirmed", true).apply()
    }

    fun exportNameSettings(): ExportNameSettings = ExportNameSettings(
        startWasConfirmed = store.getBoolean("start_time_was_confirmed", false),
        endWasConfirmed = store.getBoolean("end_time_was_confirmed", false),
    )

    fun savedExportFileName(): String? = store.getString("export_file_name", null)

    fun saveExportFileName(fileName: String) {
        store.edit().putString("export_file_name", fileName.trim()).apply()
    }

    fun preferredReportFormat(): String = store.getString("preferred_report_format", "ZIP") ?: "ZIP"

    fun savePreferredReportFormat(format: String) {
        store.edit().putString("preferred_report_format", format.uppercase()).apply()
    }

    fun exactAlarmEnabled(): Boolean = store.getBoolean("exact_alarm_enabled", true)

    fun setExactAlarmEnabled(enabled: Boolean) {
        store.edit().putBoolean("exact_alarm_enabled", enabled).apply()
    }

    fun nightScheduleEnabled(): Boolean = store.getBoolean("night_schedule_enabled", false)

    fun setNightScheduleEnabled(enabled: Boolean) {
        store.edit().putBoolean("night_schedule_enabled", enabled).apply()
    }

    fun nightScheduleNextStartEpochMs(): Long? =
        store.getLong("night_schedule_next_start_epoch_ms", 0L).takeIf { it > 0L }

    fun saveNightSchedule(enabled: Boolean, nextStartEpochMs: Long?) {
        store.edit()
            .putBoolean("night_schedule_enabled", enabled)
            .apply {
                if (enabled && nextStartEpochMs != null && nextStartEpochMs > 0L) {
                    putLong("night_schedule_next_start_epoch_ms", nextStartEpochMs)
                } else {
                    remove("night_schedule_next_start_epoch_ms")
                }
            }
            .apply()
    }
}

data class ExportNameSettings(
    val startWasConfirmed: Boolean,
    val endWasConfirmed: Boolean,
)
