package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaEvidenceFingerprintTest {
    @Test
    fun fingerprint_is_stable_for_identical_normalized_content() {
        val first = MediaEvidenceFingerprint.sha256("pkg", "MEDIA_METADATA", "session", "{\"title\":\"甲\"}")
        val second = MediaEvidenceFingerprint.sha256("pkg", "MEDIA_METADATA", "session", "{\"title\":\"甲\"}")

        assertEquals(first, second)
        assertNotEquals(first, MediaEvidenceFingerprint.sha256("pkg", "MEDIA_METADATA", "session", "{\"title\":\"乙\"}"))
    }
}
