package com.sanka1610.reprodroid.ui

import org.junit.Assert.assertEquals
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
}
