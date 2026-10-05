package com.toyquaise.kaptan.logic

/**
 * When full-screen ads may interrupt: only between levels, not before the player has finished
 * [FIRST] levels, then once every [EVERY] finished levels and at least [GAP] seconds apart.
 */
class AdPacing(private val clock: () -> Long = System::currentTimeMillis) {
    private var finished = 0
    private var sinceShown = 0
    private var lastShown: Long? = null

    fun levelFinished() {
        finished++
        sinceShown++
    }

    /** Asked when the player moves on to the next level; true means show one now. */
    fun due(): Boolean {
        if (finished < FIRST || sinceShown < EVERY) return false
        val last = lastShown
        if (last != null && clock() - last < GAP * 1000L) return false
        lastShown = clock()
        sinceShown = 0
        return true
    }

    companion object {
        const val FIRST = 3
        const val EVERY = 3
        const val GAP = 90
    }
}
