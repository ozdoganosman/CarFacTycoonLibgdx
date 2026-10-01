package com.toyquaise.toothfort.ui

import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.WireGauge
import com.toyquaise.toothfort.logic.game.EnemyKind

/** Every word the game shows. Turkish first; an English table can sit next to it later. */
object Strings {
    const val TITLE = "Macun Kalesi"

    fun part(kind: PartKind) = when (kind) {
        PartKind.BATTERY -> "Pil"
        PartKind.BRUSH -> "Fırça"
        PartKind.PASTE_CANNON -> "Macun\ntopu"
        PartKind.LASER -> "Lazer"
        PartKind.SWITCH -> "Anahtar"
        PartKind.FUSE -> "Sigorta"
        PartKind.RESISTOR -> "Direnç"
    }

    fun partLong(kind: PartKind) = when (kind) {
        PartKind.BATTERY -> "Pil · 3 V"
        PartKind.BRUSH -> "Diş fırçası · 3 V ister"
        PartKind.PASTE_CANNON -> "Macun topu · 6 V ister"
        PartKind.LASER -> "Beyazlatıcı lazer · 12 V ister"
        PartKind.SWITCH -> "Anahtar"
        PartKind.FUSE -> "Sigorta · 2,5 A'de atar"
        PartKind.RESISTOR -> "Direnç · 6 Ω"
    }

    fun wire(g: WireGauge) = when (g) {
        WireGauge.THIN -> "Kablo"
        WireGauge.THICK -> "Kalın\nkablo"
    }

    fun enemy(k: EnemyKind) = when (k) {
        EnemyKind.SUGAR_CUBE -> "Şeker küpü"
        EnemyKind.GUMMY_BEAR -> "Jelibon ayı"
        EnemyKind.LOLLIPOP -> "Lolipop"
    }

    const val ERASE = "Sil"
    const val METERS = "Ölçüm"
    const val START_WAVE = "Dalgayı başlat"
    const val WAVE_RUNNING = "Dalga sürüyor"
    const val WAVE = "Dalga"
    const val TOOTH = "Diş"
    const val MONEY = "Para"
    const val ROTATE = "Döndür"
    const val SELL = "Sat"
    const val REFILL = "Yeni pil"
    const val TOGGLE_ON = "Kapat"
    const val TOGGLE_OFF = "Aç"
    const val NEXT_LEVEL = "Sonraki bölüm"
    const val RETRY = "Yeniden dene"
    const val LEVEL = "Bölüm"
    const val WON = "Diş pırıl pırıl!"
    const val LOST = "Diş çürüdü!"
    const val ALL_DONE = "Şimdilik bütün bölümler bu kadar."

    const val NO_MONEY = "Yeterli para yok."
    const val BLOCKED_PART = "Buraya konmaz: yol ya da dolu bir kare."
    const val BLOCKED_WIRE = "Kablo bir parçaya yalnızca + ya da − ucundan bağlanır."
    const val LOCKED = "Bu parça sonraki bölümlerde açılır."
    const val TAP_TO_ROTATE = "Parçayı döndürmek için yeniden dokun."

    fun hint(level: Int) = when (level) {
        1 -> "Pilin + ucundan fırçaya, fırçanın öbür ucundan pilin − ucuna kablo çek. Devre kapanınca fırça çalışır."
        2 -> "Macun topu 6 V ister. İki pili uç uca (+ ile −) dizersen gerilimler toplanır: seri bağlantı. Yan yana bağlarsan gerilim aynı kalır ama piller daha uzun dayanır: paralel."
        3 -> "Uzaktaki makinelere kalın kablo çek: ince kablo gerilim kaybettirir, çok akımda erir. Lazer 12 V ister: dört pil seri."
        else -> ""
    }

    fun volts(v: Double) = "%.1f V".format(v).replace('.', ',')
    fun amps(a: Double) = "%.2f A".format(a).replace('.', ',')
    fun percent(x: Double) = "%" + (x * 100).toInt()
}
