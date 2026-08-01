package com.redmiklab.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticMigration14To15Test {
    @Test
    fun migration_adds_report_identity_with_legacy_safe_defaults() {
        val sql = DiagnosticDatabaseFactory.migration14To15Statements().joinToString("\n")

        assertTrue("ADD COLUMN reportBaseName TEXT NOT NULL DEFAULT ''" in sql)
        assertTrue("ADD COLUMN preferredReportFormat TEXT NOT NULL DEFAULT 'ZIP'" in sql)
        assertTrue("ADD COLUMN deviceModel TEXT NOT NULL DEFAULT ''" in sql)
        assertFalse("DROP TABLE" in sql.uppercase())
        assertFalse("DELETE FROM" in sql.uppercase())
    }
}
