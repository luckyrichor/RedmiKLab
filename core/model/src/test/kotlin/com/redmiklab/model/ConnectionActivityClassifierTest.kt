package com.redmiklab.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionActivityClassifierTest {
    @Test
    fun marks_a_flow_as_foreground_when_its_owner_is_the_visible_app() {
        assertEquals(
            ConnectionActivityClass.ForegroundActive,
            ConnectionActivityClassifier.classify(screenLocked = false, foregroundPackage = "com.ss.android.ugc.aweme", ownerPackage = "com.ss.android.ugc.aweme"),
        )
    }

    @Test
    fun marks_a_nonvisible_app_flow_as_background_while_screen_is_on() {
        assertEquals(
            ConnectionActivityClass.BackgroundScreenOn,
            ConnectionActivityClassifier.classify(screenLocked = false, foregroundPackage = "com.android.launcher", ownerPackage = "com.ss.android.ugc.aweme"),
        )
    }

    @Test
    fun marks_any_attributable_flow_as_background_while_locked() {
        assertEquals(
            ConnectionActivityClass.BackgroundScreenLocked,
            ConnectionActivityClassifier.classify(screenLocked = true, foregroundPackage = "com.ss.android.ugc.aweme", ownerPackage = "com.ss.android.ugc.aweme"),
        )
    }
}
