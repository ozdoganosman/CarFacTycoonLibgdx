package com.toyquaise.toothfort

import com.badlogic.gdx.graphics.Color

/**
 * Toyquaise's play-dough colours (toyquaise.com, art/scenes.mjs `clayColors` and styles.css),
 * plus the few the game adds for its own world.
 */
object Palette {
    private fun hex(s: String): Color = Color.valueOf(s)

    // Shared with the studio's clay art.
    val turquoise = hex("16ae9f")
    val teal = hex("0e5c56")
    val yellow = hex("f3b52a")
    val coral = hex("ec5a3f")
    val pink = hex("ee82b0")
    val grape = hex("7a5fdc")
    val cream = hex("f1e6d2")
    val charcoal = hex("2e3336")
    val brick = hex("c4442f")
    val wood = hex("dfae78")
    val glass = hex("a7d2dc")
    val line = hex("cdbb9d")

    // The site's page colours.
    val table = hex("e8f4f1")
    val ink = hex("0c3f3b")
    val inkSoft = hex("2e5e5a")
    val surface = hex("fbfefd")
    val mintLine = hex("c7e0da")

    // The game's own.
    val board = hex("5cc8b6")
    val boardLip = hex("2e9e90")
    val boardDot = hex("45b7a4")
    val meterOk = hex("8ee8d6")
    val tray = hex("3fb0a1")
    val gum = hex("f39cbe")
    val gumDark = hex("e67fa9")
    val enamel = hex("fbf7ee")
    val sugar = hex("fdfbf6")
    val steel = hex("b9c4c6")
    val smoke = hex("6d7476")
}
