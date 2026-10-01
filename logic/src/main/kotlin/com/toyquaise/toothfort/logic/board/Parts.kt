package com.toyquaise.toothfort.logic.board

/** What a part does in the circuit. */
enum class Role { BATTERY, MACHINE, SWITCH, FUSE, RESISTOR }

/** How a machine fights. */
enum class Attack {
    /** Scrubs the nearest candy in reach, many small hits. */
    SCRUB,

    /** Lobs a ball of paste that splashes. */
    BLOB,

    /** A steady beam that burns while it stays on the target. */
    BEAM,
}

/**
 * A machine is a load that wants its rated voltage. Its resistance follows from the rating
 * (R = V² / P), so a machine on half its voltage draws a quarter of its power.
 * [range] is in cells, [interval] in seconds between hits (for a beam, 1 s), [damage] per hit.
 */
class MachineSpec(
    val volts: Double,
    val watts: Double,
    val attack: Attack,
    val range: Double,
    val interval: Double,
    val damage: Double,
    val splash: Double = 0.0,
) {
    val ohms: Double get() = volts * volts / watts
    val amps: Double get() = watts / volts
}

enum class PartKind(val role: Role, val cost: Int, val machine: MachineSpec? = null) {
    BATTERY(Role.BATTERY, 10),
    BRUSH(Role.MACHINE, 15, MachineSpec(volts = 3.0, watts = 1.5, attack = Attack.SCRUB, range = 1.5, interval = 0.25, damage = 3.0)),
    PASTE_CANNON(Role.MACHINE, 30, MachineSpec(volts = 6.0, watts = 6.0, attack = Attack.BLOB, range = 2.6, interval = 1.1, damage = 16.0, splash = 0.8)),
    LASER(Role.MACHINE, 60, MachineSpec(volts = 12.0, watts = 24.0, attack = Attack.BEAM, range = 3.5, interval = 1.0, damage = 32.0)),
    SWITCH(Role.SWITCH, 5),
    FUSE(Role.FUSE, 4),
    RESISTOR(Role.RESISTOR, 4);

    /** Batteries care which way round they sit; machines, switches and fuses do not. */
    val polar: Boolean get() = role == Role.BATTERY
}

enum class WireGauge(val cost: Int, val ohmsPerSegment: Double, val maxAmps: Double) {
    THIN(cost = 1, ohmsPerSegment = 0.12, maxAmps = 2.0),
    THICK(cost = 3, ohmsPerSegment = 0.03, maxAmps = 10.0),
}

/** Electrical constants shared by the board. */
object Electric {
    /** One cell: a 3 V battery. Two in series make 6 V, four make 12 V. */
    const val BATTERY_EMF = 3.0
    const val BATTERY_OHMS = 0.25

    /** Charge in coulombs (amp-seconds): a brush (0.5 A) runs about four minutes on one battery. */
    const val BATTERY_CAPACITY = 120.0

    /** Below this charge a battery counts as empty. */
    const val EMPTY_CHARGE = 0.01

    /** Two terminals pressed against each other. */
    const val CONTACT_OHMS = 0.002
    const val SWITCH_OHMS = 0.01
    const val FUSE_OHMS = 0.01
    const val FUSE_AMPS = 2.5
    const val RESISTOR_OHMS = 6.0

    /** Below this share of its voltage a machine stalls. */
    const val STALL_RATIO = 0.35

    /** A machine tolerates this much over its voltage (as a ratio) before it starts heating. */
    const val SAFE_RATIO = 1.1

    /** Heat per second per unit of excess power; a machine burns out at heat 1. */
    const val MACHINE_HEATING = 0.18
    const val MACHINE_COOLING = 0.25
    const val WIRE_HEATING = 0.6
    const val WIRE_COOLING = 0.5

    /**
     * How well a machine works on [ratio] of its rated voltage: its share of the rated power
     * (V²), nothing once it stalls, and a little extra (and heat) above the rating.
     */
    fun performance(ratio: Double): Double {
        val r = kotlin.math.abs(ratio)
        if (r < STALL_RATIO) return 0.0
        val power = r * r
        return if (power <= 1.0) power else minOf(1.4, 1.0 + 0.5 * (power - 1.0))
    }

    /** The EMF of a battery at [charge] (0..1): steady until nearly empty, then it falls off. */
    fun batteryEmf(charge: Double): Double = when {
        charge <= 0.0 -> 0.0
        charge < 0.1 -> BATTERY_EMF * 0.92 * (charge / 0.1)
        else -> BATTERY_EMF * (0.92 + 0.08 * charge)
    }
}
