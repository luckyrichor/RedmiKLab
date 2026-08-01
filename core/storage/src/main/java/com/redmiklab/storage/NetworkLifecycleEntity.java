package com.redmiklab.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "network_lifecycle")
public final class NetworkLifecycleEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public final String runId;
    public final long timestampEpochMs;
    @NonNull public final String oldState;
    @NonNull public final String newState;
    @Nullable public final String physicalNetworkId;
    @Nullable public final String vpnNetworkId;
    @Nullable public final String vpnUnderlyingNetworkId;
    @NonNull public final String reason;
    public final int retryAttempt;
    public final long generation;

    public NetworkLifecycleEntity(@NonNull String runId, long timestampEpochMs,
            @NonNull String oldState, @NonNull String newState,
            @Nullable String physicalNetworkId, @Nullable String vpnNetworkId,
            @Nullable String vpnUnderlyingNetworkId, @NonNull String reason,
            int retryAttempt, long generation) {
        this.runId = runId;
        this.timestampEpochMs = timestampEpochMs;
        this.oldState = oldState;
        this.newState = newState;
        this.physicalNetworkId = physicalNetworkId;
        this.vpnNetworkId = vpnNetworkId;
        this.vpnUnderlyingNetworkId = vpnUnderlyingNetworkId;
        this.reason = reason;
        this.retryAttempt = retryAttempt;
        this.generation = generation;
    }
}
