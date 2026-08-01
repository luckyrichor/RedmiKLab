package com.redmiklab.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "capture_gaps")
public final class CaptureGapEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public final String runId;
    public final long startedAtEpochMs;
    @Nullable public final Long endedAtEpochMs;
    @NonNull public final String reason;
    public final long systemMobileBytes;
    @Nullable public final String details;

    public CaptureGapEntity(@NonNull String runId, long startedAtEpochMs,
            @Nullable Long endedAtEpochMs, @NonNull String reason,
            long systemMobileBytes, @Nullable String details) {
        this.runId = runId;
        this.startedAtEpochMs = startedAtEpochMs;
        this.endedAtEpochMs = endedAtEpochMs;
        this.reason = reason;
        this.systemMobileBytes = systemMobileBytes;
        this.details = details;
    }
}
