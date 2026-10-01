package com.toyquaise.toothfort.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.toyquaise.toothfort.Options
import com.toyquaise.toothfort.ToothFortGame
import com.toyquaise.toothfort.ui.Strings

/**
 * Desktop window shaped like a phone held upright.
 *   --level N          open level N (1, 2, 3)
 *   --demo             build a sample defence and play part of the first wave
 *   --seconds S        how far into the wave the demo plays (default 6)
 *   --screenshot FILE  save a PNG after the first frames and quit
 *   --size WxH         window size (default 540x960)
 *   --close-up X,Y     look closely at one cell
 *   --no-hud           draw only the 3D board
 */
fun main(args: Array<String>) {
    fun arg(name: String): String? = args.indexOf(name).takeIf { it >= 0 && it + 1 < args.size }?.let { args[it + 1] }
    val size = arg("--size")?.split("x")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 } ?: listOf(540, 960)
    val options = Options(
        level = (arg("--level")?.toIntOrNull() ?: 1) - 1,
        demo = "--demo" in args,
        screenshot = arg("--screenshot")?.let { java.io.File(it).absolutePath },
        seconds = arg("--seconds")?.toFloatOrNull() ?: 6f,
        hideHud = "--no-hud" in args,
        closeUp = arg("--close-up")?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }?.let { it[0] to it[1] },
    )
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle(Strings.TITLE)
        setWindowedMode(size[0], size[1])
        useVsync(true)
        setForegroundFPS(60)
        setBackBufferConfig(8, 8, 8, 8, 16, 0, 4)
    }
    Lwjgl3Application(ToothFortGame(options), config)
}
