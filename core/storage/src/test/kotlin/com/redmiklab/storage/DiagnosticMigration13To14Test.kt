package com.redmiklab.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticMigration13To14Test {
    @Test
    fun migration_creates_layered_tables_and_adds_legacy_safe_columns() {
        val sql = DiagnosticDatabaseFactory.migration13To14Statements().joinToString("\n")

        assertTrue("CREATE TABLE IF NOT EXISTS `network_lifecycle`" in sql)
        assertTrue("CREATE TABLE IF NOT EXISTS `traffic_accounting`" in sql)
        assertTrue("CREATE TABLE IF NOT EXISTS `capture_gaps`" in sql)
        assertTrue("ALTER TABLE app_evidence ADD COLUMN source TEXT NOT NULL DEFAULT 'LEGACY'" in sql)
        assertTrue("ALTER TABLE connection_flows ADD COLUMN direction TEXT NOT NULL DEFAULT 'UP'" in sql)
        assertTrue("ALTER TABLE connection_flows ADD COLUMN stage TEXT NOT NULL DEFAULT 'TUNNEL_OBSERVED'" in sql)
        assertTrue("ALTER TABLE connection_flows ADD COLUMN outcome TEXT NOT NULL DEFAULT 'OBSERVED'" in sql)
        assertTrue("ALTER TABLE connection_flows ADD COLUMN reason TEXT" in sql)
        assertTrue("ALTER TABLE snapshots ADD COLUMN guardianState TEXT NOT NULL DEFAULT 'UNKNOWN'" in sql)
        assertFalse("DROP TABLE" in sql.uppercase())
        assertFalse("DELETE FROM" in sql.uppercase())
    }
}
