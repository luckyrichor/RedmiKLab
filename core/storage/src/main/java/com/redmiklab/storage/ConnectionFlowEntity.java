package com.redmiklab.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Metadata only: no packet payload, URL path, account identifier, or video content is retained. */
@Entity(tableName = "connection_flows")
public class ConnectionFlowEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public final String runId;
    public final long timestampEpochMs;
    @NonNull public final String protocol;
    @NonNull public final String destinationAddress;
    public final int destinationPort;
    public final long wireBytes;
    @Nullable public final String endpointHost;
    @Nullable public final String ownerPackage;
    @Nullable public final String ownerLabel;
    @NonNull public final String activityClass;
    @NonNull public final String direction;
    @NonNull public final String stage;
    @NonNull public final String outcome;
    @Nullable public final String reason;

    public ConnectionFlowEntity(@NonNull String runId, long timestampEpochMs, @NonNull String protocol,
            @NonNull String destinationAddress, int destinationPort, long wireBytes,
            @Nullable String endpointHost, @Nullable String ownerPackage, @Nullable String ownerLabel,
            @NonNull String activityClass, @NonNull String direction, @NonNull String stage,
            @NonNull String outcome, @Nullable String reason) {
        this.runId = runId;
        this.timestampEpochMs = timestampEpochMs;
        this.protocol = protocol;
        this.destinationAddress = destinationAddress;
        this.destinationPort = destinationPort;
        this.wireBytes = wireBytes;
        this.endpointHost = endpointHost;
        this.ownerPackage = ownerPackage;
        this.ownerLabel = ownerLabel;
        this.activityClass = activityClass;
        this.direction = direction;
        this.stage = stage;
        this.outcome = outcome;
        this.reason = reason;
    }

    @androidx.room.Ignore
    public ConnectionFlowEntity(@NonNull String runId, long timestampEpochMs, @NonNull String protocol,
            @NonNull String destinationAddress, int destinationPort, long wireBytes,
            @Nullable String endpointHost, @Nullable String ownerPackage, @Nullable String ownerLabel,
            @NonNull String activityClass) {
        this(runId, timestampEpochMs, protocol, destinationAddress, destinationPort, wireBytes,
                endpointHost, ownerPackage, ownerLabel, activityClass,
                "UP", "TUNNEL_OBSERVED", "OBSERVED", null);
    }

    @androidx.room.Ignore
    public ConnectionFlowEntity(@NonNull String runId, long timestampEpochMs, @NonNull String protocol,
            @NonNull String destinationAddress, int destinationPort, long wireBytes,
            @Nullable String ownerPackage, @Nullable String ownerLabel, @NonNull String activityClass) {
        this(runId, timestampEpochMs, protocol, destinationAddress, destinationPort, wireBytes,
                null, ownerPackage, ownerLabel, activityClass,
                "UP", "TUNNEL_OBSERVED", "OBSERVED", null);
    }
}
