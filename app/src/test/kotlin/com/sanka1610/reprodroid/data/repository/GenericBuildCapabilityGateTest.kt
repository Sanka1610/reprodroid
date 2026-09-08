package com.sanka1610.reprodroid.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GenericBuildCapabilityGateTest {
    @Test
    fun `Codeberg requires its explicit source capability in addition to generic comparison`() {
        assertEquals(
            setOf("generic-build", "apk-comparison", "codeberg-source"),
            requiredGenericBuildCapabilities("https://codeberg.org/example/project"),
        )
        assertEquals(
            setOf("generic-build", "apk-comparison"),
            requiredGenericBuildCapabilities("https://github.com/example/project"),
        )
    }

    @Test
    fun `unknown source provider fails before Runner job creation`() {
        assertThrows(IllegalArgumentException::class.java) {
            requiredGenericBuildCapabilities("https://example.com/example/project")
        }
    }
}
