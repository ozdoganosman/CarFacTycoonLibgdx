package com.toyquaise.toothfort.logic

/** A square of the board. x grows to the right, y grows toward the player (down the screen). */
data class Cell(val x: Int, val y: Int) {
    fun step(d: Dir) = Cell(x + d.dx, y + d.dy)

    /** The direction of a neighbouring cell, or null when [other] is not next to this one. */
    fun dirTo(other: Cell): Dir? = Dir.entries.firstOrNull { step(it) == other }

    override fun toString() = "($x,$y)"
}

enum class Dir(val dx: Int, val dy: Int) {
    N(0, -1), E(1, 0), S(0, 1), W(-1, 0);

    val opposite: Dir get() = entries[(ordinal + 2) % 4]
    val clockwise: Dir get() = entries[(ordinal + 1) % 4]
}
