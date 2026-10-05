package com.toyquaise.kaptan

import com.badlogic.gdx.Preferences
import com.toyquaise.kaptan.logic.Levels

/** What the player has done, kept on the device: the best stars of each level. */
class Progress(private val prefs: Preferences?) {
    private val memory = HashMap<String, Int>()

    private fun get(key: String): Int = prefs?.getInteger(key, 0) ?: memory[key] ?: 0

    private fun put(key: String, value: Int) {
        if (prefs == null) { memory[key] = value; return }
        prefs.putInteger(key, value)
        prefs.flush()
    }

    fun stars(level: Int): Int = get("stars.$level")

    /** Keeps the better of the old and the new result. */
    fun record(level: Int, stars: Int) {
        if (stars > stars(level)) put("stars.$level", stars)
    }

    /** Level 1 is always open; every other one opens when the one before it is finished. */
    fun unlocked(level: Int): Boolean = level == 1 || stars(level - 1) > 0

    val totalStars: Int get() = Levels.all.sumOf { stars(it.number) }

    /** The first level not finished yet (or the last one). */
    val next: Int get() = Levels.all.firstOrNull { stars(it.number) == 0 }?.number ?: Levels.all.size
}
