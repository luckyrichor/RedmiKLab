package com.redmiklab.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "app_evidence")
public class AppEvidenceEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public final String runId;
    public final long timestampEpochMs;
    @NonNull public final String packageName;
    @Nullable public final String displayName;
    @NonNull public final String evidenceType;
    @NonNull public final String payloadJson;
    @NonNull public final String source;
    @Nullable public final String eventKey;
    @Nullable public final String contentFingerprint;
    @NonNull public final String metadataAvailability;

    public AppEvidenceEntity(@NonNull String runId, long timestampEpochMs,
            @NonNull String packageName, @Nullable String displayName,
            @NonNull String evidenceType, @NonNull String payloadJson,
            @NonNull String source, @Nullable String eventKey,
            @Nullable String contentFingerprint, @NonNull String metadataAvailability) {
        this.runId = runId;
        this.timestampEpochMs = timestampEpochMs;
        this.packageName = packageName;
        this.displayName = displayName;
        this.evidenceType = evidenceType;
        this.payloadJson = payloadJson;
        this.source = source;
        this.eventKey = eventKey;
        this.contentFingerprint = contentFingerprint;
        this.metadataAvailability = metadataAvailability;
    }

    @Ignore
    public AppEvidenceEntity(@NonNull String runId, long timestampEpochMs,
            @NonNull String packageName, @Nullable String displayName,
            @NonNull String evidenceType, @NonNull String payloadJson) {
        this(runId, timestampEpochMs, packageName, displayName, evidenceType, payloadJson,
                "LEGACY", null, null, "UNKNOWN");
    }
}
