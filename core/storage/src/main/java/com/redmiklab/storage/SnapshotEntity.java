package com.redmiklab.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "snapshots")
public class SnapshotEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull public final String runId;
    public final long plannedAtEpochMs;
    public final long timestampEpochMs;
    public final long delayMs;
    @NonNull public final String triggerSource;
    public final long totalMobileBytes;
    public final boolean mobileDataActive;
    @NonNull public final String radioTechnology;
    @Nullable public final Integer signalDbm;
    @Nullable public final Integer signalLevel;
    @Nullable public final Boolean roaming;
    @NonNull public final String trafficCollectionStatus;
    public final boolean screenInteractive;
    public final boolean deviceLocked;
    public final boolean charging;
    public final boolean lightIdle;
    public final boolean deepIdle;
    public final boolean batteryExempt;
    @Nullable public final String physicalNetworkId;
    @Nullable public final String vpnNetworkId;
    @Nullable public final String vpnUnderlyingNetworkId;
    @NonNull public final String guardianState;
    public final int guardianRetryAttempt;
    public final boolean captureComplete;
    @Nullable public final Long lastNetworkLostAtEpochMs;
    @Nullable public final Long lastNetworkRecoveredAtEpochMs;

    public SnapshotEntity(@NonNull String runId, long plannedAtEpochMs, long timestampEpochMs,
            long delayMs, @NonNull String triggerSource, long totalMobileBytes,
            boolean mobileDataActive, @NonNull String radioTechnology, @Nullable Integer signalDbm,
            @Nullable Integer signalLevel, @Nullable Boolean roaming, @NonNull String trafficCollectionStatus,
            boolean screenInteractive, boolean deviceLocked, boolean charging, boolean lightIdle,
            boolean deepIdle, boolean batteryExempt, @Nullable String physicalNetworkId,
            @Nullable String vpnNetworkId, @Nullable String vpnUnderlyingNetworkId,
            @NonNull String guardianState, int guardianRetryAttempt, boolean captureComplete,
            @Nullable Long lastNetworkLostAtEpochMs, @Nullable Long lastNetworkRecoveredAtEpochMs) {
        this.runId = runId;
        this.plannedAtEpochMs = plannedAtEpochMs;
        this.timestampEpochMs = timestampEpochMs;
        this.delayMs = delayMs;
        this.triggerSource = triggerSource;
        this.totalMobileBytes = totalMobileBytes;
        this.mobileDataActive = mobileDataActive;
        this.radioTechnology = radioTechnology;
        this.signalDbm = signalDbm;
        this.signalLevel = signalLevel;
        this.roaming = roaming;
        this.trafficCollectionStatus = trafficCollectionStatus;
        this.screenInteractive = screenInteractive;
        this.deviceLocked = deviceLocked;
        this.charging = charging;
        this.lightIdle = lightIdle;
        this.deepIdle = deepIdle;
        this.batteryExempt = batteryExempt;
        this.physicalNetworkId = physicalNetworkId;
        this.vpnNetworkId = vpnNetworkId;
        this.vpnUnderlyingNetworkId = vpnUnderlyingNetworkId;
        this.guardianState = guardianState;
        this.guardianRetryAttempt = guardianRetryAttempt;
        this.captureComplete = captureComplete;
        this.lastNetworkLostAtEpochMs = lastNetworkLostAtEpochMs;
        this.lastNetworkRecoveredAtEpochMs = lastNetworkRecoveredAtEpochMs;
    }

    @Ignore
    public SnapshotEntity(@NonNull String runId, long plannedAtEpochMs, long timestampEpochMs,
            long delayMs, @NonNull String triggerSource, long totalMobileBytes,
            boolean mobileDataActive, @NonNull String radioTechnology, @Nullable Integer signalDbm,
            @Nullable Integer signalLevel, @Nullable Boolean roaming, @NonNull String trafficCollectionStatus) {
        this(runId, plannedAtEpochMs, timestampEpochMs, delayMs, triggerSource, totalMobileBytes,
                mobileDataActive, radioTechnology, signalDbm, signalLevel, roaming,
                trafficCollectionStatus, false, false, false, false, false, false,
                null, null, null, "UNKNOWN", 0, true, null, null);
    }

    @Ignore
    public SnapshotEntity(@NonNull String runId, long timestampEpochMs, long totalMobileBytes,
            boolean mobileDataActive, @NonNull String radioTechnology, @Nullable Integer signalDbm,
            @Nullable Integer signalLevel, @Nullable Boolean roaming, @NonNull String trafficCollectionStatus) {
        this(
                runId,
                timestampEpochMs,
                timestampEpochMs,
                0,
                "LEGACY",
                totalMobileBytes,
                mobileDataActive,
                radioTechnology,
                signalDbm,
                signalLevel,
                roaming,
                trafficCollectionStatus,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                "UNKNOWN",
                0,
                true,
                null,
                null
        );
    }
}
