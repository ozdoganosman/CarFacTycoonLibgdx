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
    const val BLOCKED_PART = "Buraya konmaz: şurup izi, bir mutfak eşyası ya da başka bir parça var."
    const val START_WIRE = "Kabloyu bir parçanın + ya da − ucundan (ya da bir klipsten) başlat."
    const val LOCKED = "Bu parça sonraki bölümlerde açılır."
    const val TAP_TO_ROTATE = "Döndürmek için karttaki Döndür'e dokun. Pilleri uç uca yaklaştırırsan yapışırlar."

    /** Wire is sold by the span ("karış"), about one machine wide. */
    fun wireCost(perUnit: Int) = "$perUnit/karış"

    fun hint(level: Int) = when (level) {
        1 -> "Pili ve fırçayı tezgâha koy. Kablo aracıyla parmağını pilin + ucundan fırçaya, fırçanın öbür ucundan pilin − ucuna sürükle. Devre kapanınca fırça çalışır."
        2 -> "Macun topu 6 V ister. Bir pili ötekinin ucuna yaklaştır: + ile − yapışır, gerilimler toplanır (seri). Kabloyla yan yana bağlarsan gerilim aynı kalır ama piller uzun dayanır (paralel)."
        3 -> "Uzun kablo gerilim kaybettirir: uzaktaki makinelere kalın kablo çek. Lazer 12 V ister: dört pil uç uca."
        else -> ""
    }

    fun volts(v: Double) = "%.1f V".format(v).replace('.', ',')
    fun amps(a: Double) = "%.2f A".format(a).replace('.', ',')
    fun percent(x: Double) = "%" + (x * 100).toInt()
}
