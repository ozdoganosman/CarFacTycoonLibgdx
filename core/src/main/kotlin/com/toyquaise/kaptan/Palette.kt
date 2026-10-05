package com.toyquaise.kaptan

import com.badlogic.gdx.graphics.Color

/**
 * Toyquaise's play-dough colours (toyquaise.com, art/scenes.mjs `clayColors` and styles.css),
 * plus the few the game adds for its own sea.
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
    val meterOk = hex("8ee8d6")

    // The sea.
    val sea = hex("4cc3c9")
    val seaDeep = hex("2f9fb0")
    val gridLine = hex("eefaf7")
    val sand = hex("efd6a5")
    val sandDark = hex("d9b77c")
    val rock = hex("9aa5a8")
    val rockDark = hex("7b8588")
    val moss = hex("7cc36b")
    val woodDark = hex("b07c4b")
    val enamel = hex("fbf7ee")
    val sky = hex("5b8de6")
    val smoke = hex("6d7476")
}
