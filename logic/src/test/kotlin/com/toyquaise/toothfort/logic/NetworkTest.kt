package com.toyquaise.toothfort.logic

import com.toyquaise.toothfort.logic.circuit.Network
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NetworkTest {
    private val eps = 1e-9

    @Test
    fun `battery and resistor follow Ohm's law`() {
        val net = Network(2)
        val b = net.battery(neg = 0, pos = 1, emf = 3.0, internalOhms = 0.5)
        val r = net.resistor(1, 0, 5.5)
        val s = net.solve()
        assertEquals(0.5, s.resistorCurrent[r], eps) // 3 V / 6 Ω
        assertEquals(0.5, s.batteryCurrent[b], eps)
        assertEquals(2.75, s.voltage[1] - s.voltage[0], eps)
    }

    @Test
    fun `batteries in series add their voltages`() {
        // 0 -[B]- 1 -[B]- 2, load from 2 back to 0.
        val net = Network(3)
        net.battery(0, 1, 3.0, 0.1)
        net.battery(1, 2, 3.0, 0.1)
        val r = net.resistor(2, 0, 5.8)
        val s = net.solve()
        assertEquals(1.0, s.resistorCurrent[r], eps) // 6 V / 6 Ω
    }

    @Test
    fun `batteries in parallel keep the voltage and share the current`() {
        val net = Network(2)
        val a = net.battery(0, 1, 3.0, 0.2)
        val b = net.battery(0, 1, 3.0, 0.2)
        val r = net.resistor(1, 0, 2.9)
        val s = net.solve()
        assertEquals(1.0, s.resistorCurrent[r], eps) // 3 V / (2.9 + 0.1) Ω
        assertEquals(0.5, s.batteryCurrent[a], eps)
        assertEquals(0.5, s.batteryCurrent[b], eps)
    }

    @Test
    fun `separate circuits are solved on their own`() {
        val net = Network(5)
        net.battery(0, 1, 3.0, 1.0)
        val r1 = net.resistor(1, 0, 2.0)
        net.resistor(2, 3, 1.0) // no source: nothing flows
        val s = net.solve()
        assertEquals(1.0, s.resistorCurrent[r1], eps)
        assertEquals(0.0, s.voltage[2] - s.voltage[3], eps)
        assertEquals(0.0, s.voltage[4], eps) // an unconnected node
    }

    @Test
    fun `a dead short is a large but finite current`() {
        val net = Network(2)
        val b = net.battery(0, 1, 3.0, 0.25)
        net.resistor(1, 0, 1e-3)
        val s = net.solve()
        assertEquals(3.0 / 0.251, s.batteryCurrent[b], 1e-6)
    }
}
