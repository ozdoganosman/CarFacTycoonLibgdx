package com.toyquaise.vektor.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.toyquaise.vektor.Options
import com.toyquaise.vektor.VektorGame
import com.toyquaise.vektor.logic.Vec
import com.toyquaise.vektor.ui.Strings

/**
 * Desktop window shaped like a phone held upright.
 *   --level N          open level N instead of the level list
 *   --steps K          make K moves with the hints first
 *   --aim X,Y          show the move (X, Y) being aimed
 *   --aim-hint         show the hint's next move being aimed
 *   --finish           finish the level with the hints (the end panel)
 *   --screenshot FILE  save a PNG after the first frames and quit (progress is not saved)
 *   --size WxH         window size (default 540x960)
 *   --no-hud           draw only the map
 *   --autoplay         play the level by itself with the hints
 *   --record DIR       save every frame (30 per second) into DIR, then quit
 *   --seconds S        how long to record (default 12)
 */
fun main(args: Array<String>) {
    fun arg(name: String): String? = args.indexOf(name).takeIf { it >= 0 && it + 1 < args.size }?.let { args[it + 1] }
    val size = arg("--size")?.split("x")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 } ?: listOf(540, 960)
    val screenshot = arg("--screenshot")?.let { java.io.File(it).absolutePath }
    val record = arg("--record")?.let { java.io.File(it).absoluteFile.also(java.io.File::mkdirs).path }
    val options = Options(
        level = arg("--level")?.toIntOrNull(),
        steps = arg("--steps")?.toIntOrNull() ?: 0,
        aim = arg("--aim")?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 }?.let { Vec(it[0], it[1]) },
        aimHint = "--aim-hint" in args,
        finish = "--finish" in args,
        screenshot = screenshot,
        hideHud = "--no-hud" in args,
        volatile = screenshot != null || record != null,
        autoplay = "--autoplay" in args,
        record = record,
        seconds = arg("--seconds")?.toFloatOrNull() ?: 12f,
    )
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle(Strings.TITLE)
        setWindowedMode(size[0], size[1])
        useVsync(true)
        setForegroundFPS(60)
        setBackBufferConfig(8, 8, 8, 8, 16, 0, 4)
    }
    Lwjgl3Application(VektorGame(options), config)
}
