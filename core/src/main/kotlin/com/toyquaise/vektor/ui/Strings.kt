package com.toyquaise.vektor.ui

import com.toyquaise.vektor.logic.Chapter
import com.toyquaise.vektor.logic.Problem
import com.toyquaise.vektor.logic.Vec
import kotlin.math.abs
import kotlin.math.roundToInt

/** Every word the game shows. Turkish first; an English table can sit next to it later. */
object Strings {
    const val TITLE = "VEKTÖR PİLOTU"
    const val TAGLINE = "5 birim yakıt. İstediğin yöne, parça parça."
    const val CURRICULUM = "9. sınıf fizik · Kuvvet ve Hareket"

    const val PLAY = "OYNA"
    const val LEVEL = "BÖLÜM"
    const val FUEL = "YAKIT"
    const val NEXT = "SONRAKİ"
    const val AGAIN = "TEKRAR"
    const val LEVELS = "BÖLÜMLER"
    const val PRIVACY = "Gizlilik ayarları"

    const val ARRIVED = "PORTALA ULAŞTIN!"
    const val STRANDED = "YAKIT BİTTİ"
    const val STRANDED_HINT = "Kalan yakıt en kısa parçaya (1 birim) bile yetmiyor. Geri al ya da baştan başla."
    const val DEAD_END = "Bu konumdan portala yakıt yetmiyor: bir hamle geri al."
    const val HINT_FREE = "Reklam yüklenemedi; bu ipucu bizden!"
    const val ALL_DONE = "Şimdilik bütün bölümler bu kadar. Yeni sektörler yolda!"
    const val DRAG = "Gemiden parmağını sürükle, bırak!"

    const val ROAD = "Alınan yol"
    const val DISPLACEMENT = "Yer değiştirme"
    const val FUEL_USED = "Harcanan yakıt"
    const val WIND = "rüzgâr"
    const val RESULTANT = "bileşke"
    const val SQUARES = "birim"

    fun num(n: Int): String = if (n < 0) "−${-n}" else "$n"

    fun vec(v: Vec): String = "(${num(v.x)}, ${num(v.y)})"

    /** A length with two decimals and a decimal comma: "2,24". */
    fun dec(d: Double): String {
        val r = (d * 100).roundToInt()
        return "${r / 100},${"%02d".format(abs(r % 100))}"
    }

    /** A magnitude the way the lesson writes it: "5" when whole, "√5 ≈ 2,24" when not. */
    fun magnitude(v: Vec): String {
        val sq = v.lengthSquared
        val root = kotlin.math.sqrt(sq.toDouble())
        return if (root == root.roundToInt().toDouble()) "${root.roundToInt()}" else "√$sq ≈ ${dec(root)}"
    }

    fun chapter(c: Chapter) = when (c) {
        Chapter.LAUNCH -> "Kalkış"
        Chapter.BELT -> "Asteroit Kuşağı"
        Chapter.WIND -> "Güneş Rüzgârı"
    }

    fun chapterTopic(c: Chapter) = when (c) {
        Chapter.LAUNCH -> "Skaler yakıt, vektörel hamle"
        Chapter.BELT -> "Alınan yol ve yer değiştirme"
        Chapter.WIND -> "Bileşke vektör"
    }

    fun problem(p: Problem) = when (p) {
        Problem.ARRIVED -> "Zaten portaldasın."
        Problem.ZERO -> "Sıfır vektörle gidilmez: parmağını gemiden uzağa sürükle."
        Problem.NO_FUEL -> "Bu parça kalan yakıttan uzun."
        Problem.OFF_MAP -> "Bu parça haritanın dışına çıkıyor."
        Problem.ASTEROID -> "Yolda asteroit var!"
    }

    fun hint(v: Vec) = "İpucu: ${vec(v)} parçasını çek."

    fun fuelLine(used: Double) = "$FUEL_USED: ${dec(used)} / 5"

    fun roadLine(road: Double) = "$ROAD: ${dec(road)} $SQUARES"

    fun displacementLine(d: Vec) = "$DISPLACEMENT: ${vec(d)}  |${magnitude(d)}|"

    /** What each level teaches, shown when it opens. */
    fun tip(level: Int) = when (level) {
        1 -> "Yakıtın 5 birim. Gemiden portala parmağını sürükle ve bırak: çektiğin vektörün boyu kadar yakıt yanar."
        2 -> "Çapraz bir vektörün boyu Pisagor'la bulunur: (3, 3) için √18 ≈ 4,24. Kristaller yolun üstünde!"
        3 -> "Bir kristal portalın ötesinde. Geç ve geri dön: alınan yol 5, yer değiştirme 3 olur."
        4 -> "Kristaller düz yolun dışında. Sapmak yakıt ister: hepsini 5 birime sığdır."
        5 -> "Önce bir adım sola, sonra uzun bir çapraz parça. Yakıtını hesapla."
        6 -> "Asteroit duvarında tek bir boşluk var. Küçük parçalarla geç."
        7 -> "Çapraz yol kapalı. Kenarlardan dolaş: tam 5 birim!"
        8 -> "Önce yana, sonra asteroitin üstünden çapraz geç."
        9 -> "İki duvar var. Alttaki boşluktan çapraz bir parça seni yukarı taşır."
        10 -> "Asteroitlerin arasından slalom yap. Yan yana iki asteroitin arasından geçilmez, köşe köşeye olanların arasından geçilir."
        11 -> "Güneş rüzgârı her parçaya bedava (0, 1) ekler. Küçük parçalarla 5 birimden uzağa git!"
        12 -> "Her hamlede bileşke = senin parçan + rüzgâr. Boşluktan geç."
        13 -> "Rüzgâr batıya esiyor: (−1, 0). Parçanı ona göre nişanla."
        14 -> "Doğu rüzgârı ve dar bir depo: tam 5 birim."
        15 -> "Pilotluk sınavı: rüzgârla birlikte duvarın etrafından dolaş."
        else -> ""
    }
}
