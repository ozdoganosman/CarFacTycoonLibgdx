package com.toyquaise.kaptan.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdPacingTest {
    @Test
    fun `no ad for the first levels, then one every three, never too close together`() {
        var now = 0L
        val p = AdPacing { now }
        val shown = ArrayList<Int>()
        for (level in 1..12) {
            p.levelFinished()
            now += 60_000 // a minute a level
            if (p.due()) shown += level
        }
        // Level 3 is the first; after that every third level, at least 90 s apart (3 minutes here).
        assertEquals(listOf(3, 6, 9, 12), shown)
    }

    @Test
    fun `quick levels wait for the time gap`() {
        var now = 0L
        val p = AdPacing { now }
        val shown = ArrayList<Int>()
        for (level in 1..9) {
            p.levelFinished()
            now += 20_000
            if (p.due()) shown += level
        }
        // Shown after level 3 at 60 s; levels 6 and 7 (120 s, 140 s) come too soon after it; level 8 at 160 s is fine.
        assertEquals(listOf(3, 8), shown)
    }
}
