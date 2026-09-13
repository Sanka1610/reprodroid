package com.sanka1610.reprodroid.ui

import com.sanka1610.reprodroid.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DependencyPinningUiTest {
    @Test
    fun `job detail uses bounded dependency pinning labels`() {
        assertEquals(R.string.technical_dependency_none, dependencyPinningLabelResource("NONE"))
        assertEquals(R.string.technical_dependency_lockfile, dependencyPinningLabelResource("LOCKFILE"))
        assertEquals(R.string.technical_dependency_lockfile_offline, dependencyPinningLabelResource("LOCKFILE_OFFLINE"))
        assertNull(dependencyPinningLabelResource("FUTURE_MODE"))
    }

    @Test
    fun `source date epoch presentation remains exact`() {
        assertEquals("2026-04-28T16:29:47Z", sourceDateEpochInstant(1_777_393_787))
    }
}
