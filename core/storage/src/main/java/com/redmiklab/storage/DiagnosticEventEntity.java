package com.redmiklab.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "diagnostic_events")
public class DiagnosticEventEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public final String runId;
    public final long timestampEpochMs;
    @NonNull public final String eventType;
    @Nullable public final String details;

    public DiagnosticEventEntity(@NonNull String runId, long timestampEpochMs,
            @NonNull String eventType, @Nullable String details) {
        this.runId = runId;
        this.timestampEpochMs = timestampEpochMs;
        this.eventType = eventType;
        this.details = details;
    }
}
