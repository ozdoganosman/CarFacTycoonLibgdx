package com.toyquaise.vektor.logic

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.random.Random

/**
 * Not a test: a level designer's tool. With GENERATE=1 in the environment it builds maps from a
 * few asteroid patterns (walls with gaps, clusters, diagonals), places the ship, portal and
 * crystals at random, solves them, and writes the most interesting ones to build/candidates.txt.
 */
class GeneratorTest {
    private val w = 5
    private val h = 7

    private fun draw(l: Level): String = buildString {
        for (y in h downTo 0) {
            for (x in 0..w) {
                val p = Vec(x, y)
                append(when (p) {
                    l.start -> 'S'
                    l.portal -> 'P'
                    in l.asteroids -> '#'
                    in l.crystals -> '*'
                    else -> '.'
                })
            }
            appendLine()
        }
    }

    private class Candidate(val level: Level, val fuel: Double, val pieces: Int, val least: Double, val score: Double, val pattern: String)

    private fun pattern(rnd: Random, name: String): Set<Vec> = when (name) {
        "none" -> emptySet()
        "wall" -> {
            val y = rnd.nextInt(2, h - 1)
            val gap = rnd.nextInt(0, w + 1)
            (0..w).filter { it != gap }.map { Vec(it, y) }.toSet()
        }
        "zigzag" -> {
            val y1 = rnd.nextInt(2, 4)
            val y2 = y1 + 2
            val g1 = rnd.nextInt(0, 2)
            val g2 = rnd.nextInt(w - 1, w + 1)
            ((0..w).filter { it != g1 && it != g1 + 1 }.map { Vec(it, y1) } +
                (0..w).filter { it != g2 && it != g2 - 1 }.map { Vec(it, y2) }).toSet()
        }
        "cluster" -> {
            val cx = rnd.nextInt(1, w)
            val cy = rnd.nextInt(2, h - 1)
            listOf(Vec(cx, cy), Vec(cx + 1, cy), Vec(cx, cy + 1), Vec(cx - 1, cy), Vec(cx, cy - 1)).filter { rnd.nextFloat() < 0.8f }.toSet()
        }
        "diagonal" -> {
            val x0 = rnd.nextInt(0, 3)
            val y0 = rnd.nextInt(1, 4)
            val dir = if (rnd.nextBoolean()) 1 else -1
            (0..3).map { Vec(x0 + it, y0 + it * dir) }.toSet()
        }
        else -> {
            // A few lone asteroids, not touching each other.
            val out = HashSet<Vec>()
            repeat(30) {
                val p = Vec(rnd.nextInt(0, w + 1), rnd.nextInt(1, h))
                if (out.size < 5 && out.none { (it - p).lengthSquared <= 2 }) out += p
            }
            out
        }
    }.filter { it.x in 0..w && it.y in 0..h }.toSet()

    private fun sample(rnd: Random, chapter: Chapter, name: String, wind: List<Vec>): Candidate? {
        val rocks = pattern(rnd, name)
        val free = (0..w).flatMap { x -> (0..h).map { y -> Vec(x, y) } }.filter { it !in rocks }.shuffled(rnd)
        val wv = wind.random(rnd)
        val s = free.first()
        val reach = if (wv.isZero) 5.0 else 7.5
        val p = free.firstOrNull { it != s && (it - s).length in 3.0..reach } ?: return null
        val near = free.filter { it != s && it != p && Space.distanceToSegment(it, s, p) in 0.0..1.8 }
        if (near.size < 2) return null
        val level = Level(0, chapter, w, h, s, p, rocks, near.take(2), wv)
        val best = Solver.solve(level) ?: return null
        if (best.crystals < 2 || best.thrusts.size < 2) return null
        val least = Solver.leastFuel(level) ?: return null
        val straight = (p - s).length
        val blocked = Space.asteroidOnWay(level, s, p) != null
        val score = best.fuel * 2 + (best.fuel - least) * 2 + best.thrusts.size +
            (if (straight > 5) 2.0 else 0.0) + (if (blocked) 2.0 else 0.0) +
            // Prefer maps that use the screen: start low, portal high.
            (if (s.y <= 2 && p.y >= h - 2) 3.0 else 0.0)
        return Candidate(level, best.fuel, best.thrusts.size, least, score, name)
    }

    @Test
    fun generate() {
        assumeTrue(System.getenv("GENERATE") != null)
        val rnd = Random(7)
        val out = StringBuilder()
        val setups = listOf(
            Triple(Chapter.LAUNCH, "none", listOf(Vec.ZERO)),
            Triple(Chapter.BELT, "wall", listOf(Vec.ZERO)),
            Triple(Chapter.BELT, "zigzag", listOf(Vec.ZERO)),
            Triple(Chapter.BELT, "cluster", listOf(Vec.ZERO)),
            Triple(Chapter.BELT, "diagonal", listOf(Vec.ZERO)),
            Triple(Chapter.BELT, "scatter", listOf(Vec.ZERO)),
            Triple(Chapter.WIND, "none", listOf(Vec(1, 0), Vec(0, 1), Vec(-1, 0))),
            Triple(Chapter.WIND, "wall", listOf(Vec(1, 0), Vec(0, 1), Vec(-1, 0))),
            Triple(Chapter.WIND, "scatter", listOf(Vec(1, 0), Vec(0, -1), Vec(-1, 0))),
        )
        for ((chapter, name, wind) in setups) {
            val found = ArrayList<Candidate>()
            repeat(20000) { sample(rnd, chapter, name, wind)?.let(found::add) }
            out.appendLine("==================== $chapter $name (${found.size} solvable)")
            val seen = HashSet<String>()
            for (cnd in found.sortedByDescending { it.score }) {
                val map = draw(cnd.level)
                if (!seen.add(map)) continue
                if (seen.size > 8) break
                val l = cnd.level
                out.appendLine("--- fuel=${"%.2f".format(cnd.fuel)} least=${"%.2f".format(cnd.least)} pieces=${cnd.pieces} wind=${l.wind} score=${"%.1f".format(cnd.score)} plan=${Solver.solve(l)!!.thrusts}")
                out.append(map)
            }
        }
        File("build/candidates.txt").writeText(out.toString())
    }
}
