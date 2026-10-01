package com.toyquaise.toothfort.logic.circuit

import kotlin.math.abs

/**
 * A DC network of resistors and batteries, solved by nodal analysis: Kirchhoff's current law at
 * every node gives one linear equation per node (G·v = i), one node of every connected piece is
 * held at 0 V, and the rest is Gaussian elimination.
 *
 * A battery is an EMF with an internal resistance, entered as its Norton equivalent (a current
 * source EMF/r in parallel with r), so a dead short is a large but finite current, never a
 * singular matrix.
 */
class Network(val nodeCount: Int) {
    private val resistors = ArrayList<Resistor>()
    private val batteries = ArrayList<Battery>()

    private class Resistor(val a: Int, val b: Int, val ohms: Double)
    private class Battery(val neg: Int, val pos: Int, val emf: Double, val ohms: Double)

    /** Adds a resistor between nodes [a] and [b]; returns its index in [Solution.resistorCurrent]. */
    fun resistor(a: Int, b: Int, ohms: Double): Int {
        require(ohms > 0) { "resistance must be positive" }
        checkNode(a); checkNode(b)
        resistors += Resistor(a, b, ohms)
        return resistors.size - 1
    }

    /** Adds a battery whose − terminal is [neg] and + terminal is [pos]; returns its index in [Solution.batteryCurrent]. */
    fun battery(neg: Int, pos: Int, emf: Double, internalOhms: Double): Int {
        require(internalOhms > 0) { "a battery needs some internal resistance" }
        checkNode(neg); checkNode(pos)
        batteries += Battery(neg, pos, emf, internalOhms)
        return batteries.size - 1
    }

    private fun checkNode(n: Int) = require(n in 0 until nodeCount) { "node $n out of range" }

    fun solve(): Solution {
        val v = DoubleArray(nodeCount)
        if (nodeCount == 0) return Solution(v, DoubleArray(0), DoubleArray(0))

        // Group the nodes into connected pieces; each piece gets its own 0 V reference.
        val parent = IntArray(nodeCount) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val next = parent[c]; parent[c] = r; c = next }
            return r
        }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }
        resistors.forEach { union(it.a, it.b) }
        batteries.forEach { union(it.neg, it.pos) }

        val groups = (0 until nodeCount).groupBy(::find)
        for (nodes in groups.values) {
            if (nodes.size < 2) continue
            solveGroup(nodes, v)
        }

        val ri = DoubleArray(resistors.size) { (v[resistors[it].a] - v[resistors[it].b]) / resistors[it].ohms }
        val bi = DoubleArray(batteries.size) {
            val b = batteries[it]
            (b.emf - (v[b.pos] - v[b.neg])) / b.ohms
        }
        return Solution(v, ri, bi)
    }

    private fun solveGroup(nodes: List<Int>, v: DoubleArray) {
        // The first node is ground; the others are unknowns 0..m-1.
        val index = HashMap<Int, Int>(nodes.size * 2)
        for (i in 1 until nodes.size) index[nodes[i]] = i - 1
        val m = nodes.size - 1
        val g = Array(m) { DoubleArray(m) }
        val rhs = DoubleArray(m)

        fun stamp(a: Int, b: Int, conductance: Double) {
            val ia = index[a]
            val ib = index[b]
            if (ia != null) g[ia][ia] += conductance
            if (ib != null) g[ib][ib] += conductance
            if (ia != null && ib != null) {
                g[ia][ib] -= conductance
                g[ib][ia] -= conductance
            }
        }
        fun inject(node: Int, current: Double) {
            index[node]?.let { rhs[it] += current }
        }

        val members = nodes.toHashSet()
        for (r in resistors) if (r.a in members) stamp(r.a, r.b, 1.0 / r.ohms)
        for (b in batteries) if (b.pos in members) {
            stamp(b.neg, b.pos, 1.0 / b.ohms)
            inject(b.pos, b.emf / b.ohms)
            inject(b.neg, -b.emf / b.ohms)
        }

        val x = gaussianElimination(g, rhs) ?: return
        for (i in 1 until nodes.size) v[nodes[i]] = x[i - 1]
    }

    companion object {
        /** Solves a·x = b in place with partial pivoting; null if the system is singular. */
        internal fun gaussianElimination(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
            val n = b.size
            for (col in 0 until n) {
                var pivot = col
                for (row in col + 1 until n) if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
                if (abs(a[pivot][col]) < 1e-12) return null
                if (pivot != col) {
                    val tmp = a[pivot]; a[pivot] = a[col]; a[col] = tmp
                    val tb = b[pivot]; b[pivot] = b[col]; b[col] = tb
                }
                val p = a[col][col]
                for (row in col + 1 until n) {
                    val f = a[row][col] / p
                    if (f == 0.0) continue
                    for (k in col until n) a[row][k] -= f * a[col][k]
                    b[row] -= f * b[col]
                }
            }
            val x = DoubleArray(n)
            for (row in n - 1 downTo 0) {
                var s = b[row]
                for (k in row + 1 until n) s -= a[row][k] * x[k]
                x[row] = s / a[row][row]
            }
            return x
        }
    }
}

/**
 * Node voltages, and currents: a resistor's current flows from its first node to its second; a
 * battery's current flows out of its + terminal (negative while something charges it).
 */
class Solution(
    val voltage: DoubleArray,
    val resistorCurrent: DoubleArray,
    val batteryCurrent: DoubleArray,
)
