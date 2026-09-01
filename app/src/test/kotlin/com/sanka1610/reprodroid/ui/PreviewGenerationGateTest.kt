package com.sanka1610.reprodroid.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewGenerationGateTest {
    @Test
    fun `new URL makes an older response stale`() {
        val gate = PreviewGenerationGate()
        val first = gate.begin("https://github.com/example/first")
        val second = gate.begin("https://github.com/example/second")

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun `repeating the same URL still rejects an older response`() {
        val gate = PreviewGenerationGate()
        val first = gate.begin("https://github.com/example/repository")
        val second = gate.begin("https://github.com/example/repository")

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun `clear invalidates preview and in-flight registration tokens`() {
        val gate = PreviewGenerationGate()
        val request = gate.begin("https://github.com/example/repository")

        gate.invalidate()

        assertFalse(gate.isCurrent(request))
    }
}
