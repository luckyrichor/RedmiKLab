package com.redmiklab.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectIdentityTest {
    @Test
    fun exposes_the_stable_project_name() {
        assertEquals("redmi-klab", ProjectIdentity.name)
    }
}
