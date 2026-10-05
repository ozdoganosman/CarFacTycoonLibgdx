package com.toyquaise.kaptan.ui

import com.toyquaise.kaptan.logic.Chapter
import com.toyquaise.kaptan.logic.Factor
import com.toyquaise.kaptan.logic.Problem
import com.toyquaise.kaptan.logic.Stretch
import com.toyquaise.kaptan.logic.Vec
import kotlin.math.abs
import kotlin.math.roundToInt

/** Every word the game shows. Turkish first; an English table can sit next to it later. */
object Strings {
    const val TITLE = "Hamur Kaptan"
    const val SUBTITLE = "Vektörlerle limana"
    const val CURRICULUM = "9. sınıf fizik · Kuvvet ve Hareket"

    const val PLAY = "Oyna"
    const val GO = "Yola çık"
    const val UNDO = "Geri al"
    const val RESET = "Baştan"
    const val HINT = "İpucu"
    const val LEVELS = "Bölümler"
    const val LEVEL = "Bölüm"
    const val MOVES = "Hamle"
    const val NEXT = "Sonraki bölüm"
    const val AGAIN = "Tekrar oyna"
    const val PRIVACY = "Gizlilik ayarları"
    const val TOGETHER = "Birlikte"
    const val SPLIT = "Ayır"
    const val REMAINING = "Limana kalan"
    const val CURRENT = "Akıntı"
    const val CURRENT_SHORT = "akıntı"
    const val DISPLACEMENT_SHORT = "yer değiştirme"
    const val SQUARES = "kare"

    const val ARRIVED = "Limana vardın!"
    const val BEST = "en iyi"
    const val ROAD = "Alınan yol"
    const val DISPLACEMENT = "Yer değiştirme"
    const val ALL_DONE = "Şimdilik bütün bölümler bu kadar. Yeni denizler yolda!"

    const val PICK_FIRST = "Önce bir kart seç."
    const val PICK_SECOND = "Birlikte çekmek için ikinci kartı da seç."
    const val NO_PAIR_TOKEN = "Bu bölümde römorkör yok."
    const val OUT_OF_CARDS = "Kartlar bitti. Geri al ya da baştan başla."
    const val DEAD_END = "Bu konumdan limana varılamıyor: bir hamle geri al."
    const val HINT_FREE = "Reklam yüklenemedi; bu ipucu bizden!"
    const val TOGETHER_ON = "İki kart seç: iki römorkör tekneyi birlikte çeker, tekne paralelkenarın köşegeninden gider."

    fun num(n: Int): String = if (n < 0) "−${-n}" else "$n"

    fun vec(v: Vec): String = "(${num(v.x)}, ${num(v.y)})"

    /** A length in squares: whole numbers plainly, others with one decimal ("7,2"). */
    fun length(d: Double): String {
        val r = (d * 10).roundToInt()
        return if (r % 10 == 0) "${r / 10}" else "${r / 10},${abs(r % 10)}"
    }

    fun factor(f: Factor): String {
        val n = when (f.stretch) {
            Stretch.NONE -> "1"
            Stretch.DOUBLE -> "2"
            Stretch.HALF -> "½"
        }
        return if (f.flip) "×(−$n)" else "×$n"
    }

    const val DOUBLE = "×2"
    const val HALF = "×½"
    const val FLIP = "×(−1)"

    fun chapter(c: Chapter) = when (c) {
        Chapter.CANAL -> "Kanal"
        Chapter.HARBOR -> "Liman"
        Chapter.CURRENT -> "Akıntı"
        Chapter.TUGBOATS -> "Römorkörler"
    }

    fun chapterTopic(c: Chapter) = when (c) {
        Chapter.CANAL -> "Aynı doğrultudaki vektörler"
        Chapter.HARBOR -> "Uç uca ekleme"
        Chapter.CURRENT -> "Bileşke vektör"
        Chapter.TUGBOATS -> "Bileşenler ve paralelkenar"
    }

    fun problem(p: Problem) = when (p) {
        Problem.ARRIVED -> "Zaten limandasın."
        Problem.NO_CARD -> PICK_FIRST
        Problem.SAME_CARD -> "Aynı kart iki römorköre birden verilemez."
        Problem.TOO_MANY -> "En çok iki kart birlikte çekebilir."
        Problem.NO_DOUBLE -> "×2 jetonun kalmadı."
        Problem.NO_HALF -> "×½ jetonun kalmadı."
        Problem.NO_FLIP -> "×(−1) jetonun kalmadı."
        Problem.NO_PAIR -> "Römorkör jetonun kalmadı."
        Problem.ODD_HALF -> "Bu kartın yarısı tam kareye denk gelmiyor: bileşenleri çift sayı olmalı."
        Problem.NO_MOVE -> "Akıntı bu hamleyi sıfırlıyor: tekne yerinde kalır."
        Problem.OFF_SEA -> "Bu hamle tekneyi denizin dışına çıkarır."
        Problem.ROCK -> "Bu yolda kaya var: tekne karaya oturur."
        Problem.NO_SPLIT -> "Ayırma jetonun kalmadı."
        Problem.CANT_SPLIT -> "Bu kart zaten bir eksen boyunca: bileşenleri kendisi."
    }

    fun split(v: Vec): String {
        val (x, y) = v.components
        return "${vec(v)} = ${vec(x)} + ${vec(y)}: vektör, bileşenlerinin toplamıdır."
    }

    fun hintMove(cards: List<Pair<Vec, Factor>>): String {
        val parts = cards.joinToString(" ve ") { (v, f) -> if (f.isOne) vec(v) else "${vec(v)} ${factor(f)}" }
        return if (cards.size == 2) "İpucu: $parts kartlarını birlikte çek." else "İpucu: $parts kartıyla devam et."
    }

    fun hintSplit(v: Vec) = "İpucu: ${vec(v)} kartını bileşenlerine ayır."

    fun movesLine(plays: Int, best: Int) = "$MOVES: $plays ($BEST: $best)"

    fun roadLine(travelled: Double) = "$ROAD: ${length(travelled)} $SQUARES"

    fun displacementLine(d: Vec) = "$DISPLACEMENT: ${vec(d)}, büyüklüğü ${length(d.length)} $SQUARES"

    /** What each level teaches, shown when it opens. */
    fun tip(level: Int) = when (level) {
        1 -> "Kartlar vektördür: (0, 4) kuzeye 4 kare demek. Kartı seç, oku gör, Yola çık'a bas."
        2 -> "Vektörler uç uca eklenir: ikinci ok, birincinin bittiği yerden başlar. Hangi iki kart limana götürür?"
        3 -> "(0, −2) güneye bakar: zıt yönlü vektör toplamı küçültür. Sıraya dikkat, kanalın dışına çıkma!"
        4 -> "×2 jetonu kartı bir gerçek sayıyla çarpar: yön aynı kalır, boy iki katına çıkar."
        5 -> "×(−1) kartı zıt vektörüne çevirir: boy aynı, yön ters."
        6 -> "×½ kartın boyunu yarıya indirir. Tek hamlede varabilir misin?"
        7 -> "Bileşke vektör, çıkıştan varışa giden oktur. 3 doğu ve 4 kuzey: yol 7 kare, yer değiştirme 5 kare!"
        8 -> "Toplamada sıra bileşkeyi değiştirmez ama rotayı değiştirir. Kayaya dikkat!"
        9 -> "Eğik kartların x ve y bileşenleri ayrı ayrı toplanır: (2, 1) + (1, 3) = (3, 4)."
        10 -> "Kart fazla! Limana kalan vektörü bul, toplamı ona eşit olan kartları seç."
        11 -> "Akıntı her hamleye (1, 0) ekler: kart oku, akıntı oku ve bileşke bir üçgen çizer."
        12 -> "Akıntı güneye çekiyor. Her hamlede bileşke = kart + akıntı."
        13 -> "Akıntı ve ×2 jetonu birlikte. Önce hesapla, sonra yola çık."
        14 -> "Kaya dümdüz yolu kapatıyor. Ayır'a bas: (3, 4) kartı (3, 0) ve (0, 4) bileşenlerine ayrılır."
        15 -> "İki köşe de kapalı. Birlikte'ye bas ve iki kart seç: tekne paralelkenarın köşegeninden gider."
        16 -> "Kaptanlık sınavı: ayırma, birlikte çekme, ×(−1) ve akıntı bir arada. Kolay gelsin, kaptan!"
        else -> ""
    }
}
