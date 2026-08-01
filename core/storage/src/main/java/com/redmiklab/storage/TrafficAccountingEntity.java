package com.redmiklab.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "traffic_accounting")
public final class TrafficAccountingEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public final String runId;
    public final long windowStartEpochMs;
    public final long windowEndEpochMs;
    @Nullable public final String packageName;
    @NonNull public final String layer;
    @NonNull public final String direction;
    public final long bytes;
    @NonNull public final String outcome;
    @Nullable public final String reason;

    public TrafficAccountingEntity(@NonNull String runId, long windowStartEpochMs,
            long windowEndEpochMs, @Nullable String packageName, @NonNull String layer,
            @NonNull String direction, long bytes, @NonNull String outcome,
            @Nullable String reason) {
        this.runId = runId;
        this.windowStartEpochMs = windowStartEpochMs;
        this.windowEndEpochMs = windowEndEpochMs;
        this.packageName = packageName;
        this.layer = layer;
        this.direction = direction;
        this.bytes = bytes;
        this.outcome = outcome;
        this.reason = reason;
    }
}
