package com.sanka1610.reprodroid.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DependencyPinningUiTest {
    @Test
    fun `job detail uses bounded dependency pinning labels`() {
        assertEquals("None", dependencyPinningLabel("NONE"))
        assertEquals("Lockfile checked", dependencyPinningLabel("LOCKFILE"))
        assertEquals(
            "Lockfile checked · Gradle offline resolution",
            dependencyPinningLabel("LOCKFILE_OFFLINE"),
        )
    }

    @Test
    fun `determinism labels are exact and do not claim guaranteed reproducibility`() {
        assertEquals("Not configured", determinismSummary(null, false, null))
        val configured = determinismSummary(1_777_393_787, true, "C.UTF-8")
        assertEquals(
            "SOURCE_DATE_EPOCH 1777393787 (2026-04-28T16:29:47Z) · " +
                "Gradle build cache disabled by Runner · process locale C.UTF-8",
            configured,
        )
        assertFalse(configured.contains("guarantee", ignoreCase = true))
        assertFalse(configured.contains("all caches", ignoreCase = true))
    }
}
