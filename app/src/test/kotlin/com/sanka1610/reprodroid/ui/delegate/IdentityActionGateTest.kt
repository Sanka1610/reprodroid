package com.sanka1610.reprodroid.ui.delegate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityActionGateTest {
    @Test
    fun `same identity cannot be acquired twice`() {
        val gate = IdentityActionGate()

        assertTrue(gate.tryAcquire("app-a"))
        assertFalse(gate.tryAcquire("app-a"))
        assertEquals(setOf("app-a"), gate.activeIds.value)
    }

    @Test
    fun `different identities preserve independent concurrency`() {
        val gate = IdentityActionGate()

        assertTrue(gate.tryAcquire("app-a"))
        assertTrue(gate.tryAcquire("app-b"))
        assertEquals(setOf("app-a", "app-b"), gate.activeIds.value)
    }

    @Test
    fun `released identity can be acquired again`() {
        val gate = IdentityActionGate()
        assertTrue(gate.tryAcquire("app-a"))

        gate.release("app-a")

        assertTrue(gate.tryAcquire("app-a"))
    }

    @Test
    fun `storage gate admits exactly one shared action`() {
        val gate = SingleActionGate()

        assertTrue(gate.tryAcquire())
        assertTrue(gate.busy.value)
        assertFalse(gate.tryAcquire())
        gate.release()
        assertFalse(gate.busy.value)
        assertTrue(gate.tryAcquire())
    }
}
