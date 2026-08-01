package com.redmiklab.storage;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "app_traffic")
public class AppTrafficEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public final String runId;
    public final long windowStartEpochMs;
    public final long windowEndEpochMs;
    @NonNull public final String packageName;
    @NonNull public final String displayName;
    public final long mobileBytes;
    public final boolean approximate;

    public AppTrafficEntity(@NonNull String runId, long windowStartEpochMs, long windowEndEpochMs,
            @NonNull String packageName, @NonNull String displayName, long mobileBytes, boolean approximate) {
        this.runId = runId;
        this.windowStartEpochMs = windowStartEpochMs;
        this.windowEndEpochMs = windowEndEpochMs;
        this.packageName = packageName;
        this.displayName = displayName;
        this.mobileBytes = mobileBytes;
        this.approximate = approximate;
    }
}
