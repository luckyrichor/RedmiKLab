package com.redmiklab.storage;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {SnapshotEntity.class, DiagnosticRunEntity.class, ProbeEntity.class,
        ProbeAttemptEntity.class, AppTrafficEntity.class, ConnectionFlowEntity.class,
        DiagnosticEventEntity.class, AppEvidenceEntity.class, NetworkLifecycleEntity.class,
        TrafficAccountingEntity.class, CaptureGapEntity.class}, version = 15, exportSchema = false)
public abstract class DiagnosticDatabase extends RoomDatabase {
    public abstract DiagnosticDao diagnosticDao();
}
