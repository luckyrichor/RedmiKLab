package com.redmiklab.storage;

import android.content.Context;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public final class DiagnosticDatabaseFactory {
    private static volatile DiagnosticDatabase instance;

    private DiagnosticDatabaseFactory() { }

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE snapshots ADD COLUMN mobileDataActive INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE snapshots ADD COLUMN radioTechnology TEXT NOT NULL DEFAULT 'Unavailable'");
            database.execSQL("ALTER TABLE snapshots ADD COLUMN signalDbm INTEGER");
            database.execSQL("ALTER TABLE snapshots ADD COLUMN signalLevel INTEGER");
            database.execSQL("ALTER TABLE snapshots ADD COLUMN roaming INTEGER");
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE snapshots ADD COLUMN trafficCollectionStatus TEXT NOT NULL DEFAULT 'UNKNOWN'");
        }
    };

    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `probe_attempts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `runId` TEXT NOT NULL, `timestampEpochMs` INTEGER NOT NULL, `endpoint` TEXT NOT NULL, `attemptNumber` INTEGER NOT NULL, `dnsLatencyMs` INTEGER, `httpsLatencyMs` INTEGER, `consumedBytes` INTEGER NOT NULL, `failure` TEXT)");
        }
    };

    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `connection_flows` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `runId` TEXT NOT NULL, `timestampEpochMs` INTEGER NOT NULL, `protocol` TEXT NOT NULL, `destinationAddress` TEXT NOT NULL, `destinationPort` INTEGER NOT NULL, `wireBytes` INTEGER NOT NULL, `ownerPackage` TEXT, `ownerLabel` TEXT, `activityClass` TEXT NOT NULL)");
        }
    };

    private static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE diagnostic_runs ADD COLUMN plannedStartEpochMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE diagnostic_runs ADD COLUMN plannedEndEpochMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE diagnostic_runs ADD COLUMN runtimeMode TEXT NOT NULL DEFAULT 'STANDARD'");
            database.execSQL("ALTER TABLE diagnostic_runs ADD COLUMN snapshotMinutes INTEGER NOT NULL DEFAULT 5");
            database.execSQL("ALTER TABLE diagnostic_runs ADD COLUMN connectivityMinutes INTEGER NOT NULL DEFAULT 10");
            database.execSQL("ALTER TABLE diagnostic_runs ADD COLUMN throughputMinutes INTEGER NOT NULL DEFAULT 30");
            database.execSQL("ALTER TABLE diagnostic_runs ADD COLUMN connectionCaptureEnabled INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE diagnostic_runs ADD COLUMN terminalReason TEXT");
            database.execSQL("ALTER TABLE diagnostic_runs ADD COLUMN lastActionAtEpochMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("UPDATE diagnostic_runs SET plannedStartEpochMs = startedAtEpochMs, plannedEndEpochMs = endedAtEpochMs, lastActionAtEpochMs = startedAtEpochMs");
            database.execSQL("ALTER TABLE snapshots ADD COLUMN plannedAtEpochMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE snapshots ADD COLUMN delayMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE snapshots ADD COLUMN triggerSource TEXT NOT NULL DEFAULT 'LEGACY'");
            database.execSQL("UPDATE snapshots SET plannedAtEpochMs = timestampEpochMs");
            database.execSQL("CREATE TABLE IF NOT EXISTS `diagnostic_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `runId` TEXT NOT NULL, `timestampEpochMs` INTEGER NOT NULL, `eventType` TEXT NOT NULL, `details` TEXT)");
        }
    };

    private static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `app_evidence` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `runId` TEXT NOT NULL, `timestampEpochMs` INTEGER NOT NULL, `packageName` TEXT NOT NULL, `displayName` TEXT, `evidenceType` TEXT NOT NULL, `payloadJson` TEXT NOT NULL)");
        }
    };

    private static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE connection_flows ADD COLUMN endpointHost TEXT");
        }
    };

    private static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE probes ADD COLUMN plannedAtEpochMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE probes ADD COLUMN delayMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE probes ADD COLUMN triggerSource TEXT NOT NULL DEFAULT 'LEGACY'");
            database.execSQL("UPDATE probes SET plannedAtEpochMs = timestampEpochMs");
        }
    };

    private static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE probes ADD COLUMN firstAttemptAtEpochMs INTEGER");
            database.execSQL("ALTER TABLE probes ADD COLUMN lastAttemptAtEpochMs INTEGER");
            database.execSQL("ALTER TABLE probes ADD COLUMN completedAtEpochMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("UPDATE probes SET firstAttemptAtEpochMs = timestampEpochMs, lastAttemptAtEpochMs = timestampEpochMs, completedAtEpochMs = timestampEpochMs");
        }
    };

    public static String[] migration13To14Statements() {
        return new String[] {
                "CREATE TABLE IF NOT EXISTS `network_lifecycle` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `runId` TEXT NOT NULL, `timestampEpochMs` INTEGER NOT NULL, `oldState` TEXT NOT NULL, `newState` TEXT NOT NULL, `physicalNetworkId` TEXT, `vpnNetworkId` TEXT, `vpnUnderlyingNetworkId` TEXT, `reason` TEXT NOT NULL, `retryAttempt` INTEGER NOT NULL, `generation` INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS `traffic_accounting` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `runId` TEXT NOT NULL, `windowStartEpochMs` INTEGER NOT NULL, `windowEndEpochMs` INTEGER NOT NULL, `packageName` TEXT, `layer` TEXT NOT NULL, `direction` TEXT NOT NULL, `bytes` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `reason` TEXT)",
                "CREATE TABLE IF NOT EXISTS `capture_gaps` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `runId` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `endedAtEpochMs` INTEGER, `reason` TEXT NOT NULL, `systemMobileBytes` INTEGER NOT NULL, `details` TEXT)",
                "ALTER TABLE app_evidence ADD COLUMN source TEXT NOT NULL DEFAULT 'LEGACY'",
                "ALTER TABLE app_evidence ADD COLUMN eventKey TEXT",
                "ALTER TABLE app_evidence ADD COLUMN contentFingerprint TEXT",
                "ALTER TABLE app_evidence ADD COLUMN metadataAvailability TEXT NOT NULL DEFAULT 'UNKNOWN'",
                "ALTER TABLE snapshots ADD COLUMN screenInteractive INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE snapshots ADD COLUMN deviceLocked INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE snapshots ADD COLUMN charging INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE snapshots ADD COLUMN lightIdle INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE snapshots ADD COLUMN deepIdle INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE snapshots ADD COLUMN batteryExempt INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE snapshots ADD COLUMN physicalNetworkId TEXT",
                "ALTER TABLE snapshots ADD COLUMN vpnNetworkId TEXT",
                "ALTER TABLE snapshots ADD COLUMN vpnUnderlyingNetworkId TEXT",
                "ALTER TABLE snapshots ADD COLUMN guardianState TEXT NOT NULL DEFAULT 'UNKNOWN'",
                "ALTER TABLE snapshots ADD COLUMN guardianRetryAttempt INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE snapshots ADD COLUMN captureComplete INTEGER NOT NULL DEFAULT 1",
                "ALTER TABLE snapshots ADD COLUMN lastNetworkLostAtEpochMs INTEGER",
                "ALTER TABLE snapshots ADD COLUMN lastNetworkRecoveredAtEpochMs INTEGER",
                "ALTER TABLE connection_flows ADD COLUMN direction TEXT NOT NULL DEFAULT 'UP'",
                "ALTER TABLE connection_flows ADD COLUMN stage TEXT NOT NULL DEFAULT 'TUNNEL_OBSERVED'",
                "ALTER TABLE connection_flows ADD COLUMN outcome TEXT NOT NULL DEFAULT 'OBSERVED'",
                "ALTER TABLE connection_flows ADD COLUMN reason TEXT"
        };
    }

    public static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            for (String statement : migration13To14Statements()) database.execSQL(statement);
        }
    };

    public static String[] migration14To15Statements() {
        return new String[] {
                "ALTER TABLE diagnostic_runs ADD COLUMN reportBaseName TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE diagnostic_runs ADD COLUMN preferredReportFormat TEXT NOT NULL DEFAULT 'ZIP'",
                "ALTER TABLE diagnostic_runs ADD COLUMN deviceModel TEXT NOT NULL DEFAULT ''"
        };
    }

    public static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            for (String statement : migration14To15Statements()) database.execSQL(statement);
        }
    };

    public static DiagnosticDatabase get(Context context) {
        if (instance == null) {
            synchronized (DiagnosticDatabaseFactory.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), DiagnosticDatabase.class, "redmi_klab.db")
                            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                                    MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                                    MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                                    MIGRATION_14_15)
                            .build();
                }
            }
        }
        return instance;
    }
}
