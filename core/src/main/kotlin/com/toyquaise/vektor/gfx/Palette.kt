package com.toyquaise.vektor.gfx

import com.badlogic.gdx.graphics.Color

/** Neon on deep space. */
object Palette {
    private fun hex(s: String): Color = Color.valueOf(s)

    val spaceTop = hex("1b0d44")
    val spaceMid = hex("0d0a2b")
    val spaceBottom = hex("04060f")

    val cyan = hex("3ff3ff")
    val blue = hex("4d8dff")
    val magenta = hex("ff4fd8")
    val purple = hex("a05cff")
    val gold = hex("ffd23f")
    val orange = hex("ff8a3d")
    val red = hex("ff4d6d")
    val green = hex("42f5a1")
    val white = hex("f2f7ff")
    val dim = hex("8b9cc8")
    val faint = hex("4a5585")

    val rock = hex("3b3f63")
    val rockLight = hex("6b70a0")
    val rockDark = hex("23253f")

    val panel = hex("141a3acc")
    val panelEdge = hex("3ff3ff")
}
