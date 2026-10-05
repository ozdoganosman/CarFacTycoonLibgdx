# Hamur Kaptan: tasarım belgesi

Toyquaise'in öğretici oyunlarından biri. Oyuncu, kareli bir denizde hamurdan bir römorkörü
vektör kartlarıyla limana götürür. Konu, 9. sınıf fizik dersinin 2. ünitesidir: **Kuvvet ve
Hareket** (Türkiye Yüzyılı Maarif Modeli, FİZ.9.2).

## Neden bu fikir

- **Program bunu istiyor:** FİZ.9.2.4 vektör toplamayı kareli düzlemde, "simülasyon ve animasyon
  gibi dijital içerikler" ile öğretmeyi öneriyor. Oyun, öğretmenin derste açabileceği bir
  materyaldir.
- **Bulmaca türü tutar:** Tek parmakla oynanır ve bölümlüdür. Bölüm araları reklama doğal yer
  açar.
- **Dilden bağımsız:** Vektörler her dilde aynıdır. Aynı oyun ileride İngilizce olarak da
  yayımlanabilir; reklam fiyatı orada daha yüksektir.
- **Hamur görünümü:** Toyquaise'in hamur dünyası Macun Kalesi'nden (Tooth Fort) gelir.
  Görüntüleyici ve gölgelendiriciler aynıdır.

## Sahne: kareli deniz

Deniz, kumsal bir kenarın içinde turkuaz bir hamur levhadır. Üstüne açık renk hamur şeritlerden
kareler bastırılmıştır. Bu, defterdeki kareli düzlemin kendisidir: x doğuya (sağa), y kuzeye
(yukarı) artar. Kenarlarda koordinat sayıları yazar.

- **Tekne:** Kaptanıyla birlikte mercan renkli bir römorkör.
- **Liman:** Çizgili bir can simidi ve yanında küçük bir deniz feneri.
- **Kayalar:** Izgara noktalarında duran gri, yosunlu hamur topakları.
- **Çıkış noktası:** Krem renkli küçük bir halka. Yer değiştirme buradan ölçülür.
- **Akıntı:** Akıntılı bölümlerde akıntı yönünde süzülen köpük okları.

## Nasıl oynanır

1. Alttaki **kartlar** vektördür: (3, 0) "3 kare doğu", (0, −2) "2 kare güney" demektir. Her
   kartın üstünde küçük bir ok resmi vardır.
2. Bir kart seçilince denizde **önizleme okları** çıkar:
   - kart vektörü (sarı),
   - varsa akıntı (mor),
   - bileşke (mercan).
   Hamlenin bittiği yerde yarı saydam bir tekne görünür. Hamle bir kayaya çarpar ya da denizden
   çıkarsa kaya kırmızı yanıp söner ve nedeni hemen yazılır.
3. **Yola çık** ile tekne hamleyi yapar. Kart harcanır. Geçilen yol koyu bir okla denizde kalır.
4. **Jetonlar** bölüme göre verilir ve seçili karta uygulanır:
   - **×2**, **×½**: vektörü gerçek bir sayıyla çarpar. Yön aynı kalır, boy değişir.
   - **×(−1)**: zıt vektör. Boy aynı kalır, yön ters döner.
   - **Ayır**: kartı x ve y bileşenlerine ayırır, örneğin (3, 4) = (3, 0) + (0, 4). Bu bir hamle
     sayılmaz.
   - **Birlikte**: iki kart seçilir. İki römorkör tekneyi aynı anda çeker ve tekne
     paralelkenarın köşegeninden dümdüz gider.
5. **Geri al** son hamleyi ya da ayırmayı geri alır. **Baştan** bölümü yeniden başlatır.
   İkisi de ücretsizdir.
6. **İpucu** (ödüllü reklam): en iyi çözümün sıradaki adımını seçer ve açıklar. Reklam
   yüklenemezse ipucu yine verilir. Çıkmaz bir konumda reklam gösterilmez; "bir hamle geri al"
   denir.
7. Limana varınca bölüm sonu kartı açılır:
   - yıldızlar,
   - hamle sayısı ve en iyisi,
   - alınan yol,
   - yer değiştirme (vektörü ve büyüklüğü),
   - "yer değiştirme, alınan yoldan kısadır" notu.

## Kurallar (kesin hâli `logic/` içinde)

- **Konumlar:** Tekne ve kayalar ızgara noktalarındadır. Denizin noktaları (0, 0) ile
  (genişlik, yükseklik) arasındadır.
- **Hamle:** Seçilen kart vektörlerinin (çarpanlarıyla) ve akıntının toplamıdır. Tekne bu
  bileşke boyunca dümdüz gider.
- **Kaya:** Yolun bir kayaya uzaklığı 0,35 kareden azsa tekne karaya oturur ve hamle yapılamaz.
  Örnekler:
  - (1, 3) eğimli bir yol, yanındaki noktaya 0,32 kare yaklaşır: geçemez.
  - (1, 2) eğimli bir yol 0,45 kare uzaktan geçer: geçer.
  - İki kayanın arasından çapraz geçmek serbesttir.
- **Deniz sınırı:** Hamle denizin dışında biterse yapılamaz.
- **Akıntı sıfırlarsa:** Akıntının bir kartı tam sıfırladığı hamle yapılamaz.
- **×½:** Yalnızca bileşenleri çift olan karta uygulanır; yarım kare yoktur.
- **Yıldızlar:**
  - 3 yıldız: en az hamle ve o hamle sayısıyla en kısa yol.
  - 2 yıldız: en iyiden en çok bir fazla hamle.
  - 1 yıldız: limana varmak.
  - Bir bölüm, öncekinde en az bir yıldız alınca açılır.

**Çözücü** (`logic/.../Solver.kt`) bütün olasılıkları dener ve gördüğü durumları hatırlar. Her
bölüm için en iyi yolu (önce en az hamle, sonra en kısa yol) bulur. Üç işi var:

- yıldızların ölçüsünü belirlemek,
- ipucu vermek,
- testlerde her bölümün bitirilebildiğini ve öğretmesi gerekeni gerçekten öğrettiğini kanıtlamak.
  Örneğin 14. bölüm ayırmadan, 15. bölüm birlikte çekmeden bitmez; 4, 5, 6 ve 13. bölümler
  jetonsuz aynı iyilikte bitmez.

## Kazanımlar

| Kazanım | Oyundaki karşılığı | Durum |
|---|---|---|
| FİZ.9.2.3 Aynı doğrultudaki vektörler; eşit, zıt ve gerçek sayıyla çarpılmış vektör | Kanal bölümleri (1–6): uç uca toplama, zıt kart, ×2, ×½, ×(−1) | Oynanır |
| FİZ.9.2.4 Uç uca ekleme | Liman bölümleri (7–10): sıranın bileşkeyi değil rotayı değiştirmesi | Oynanır |
| FİZ.9.2.4 Bileşke vektör | Akıntı bölümleri (11–13): kart + akıntı = bileşke üçgeni | Oynanır |
| FİZ.9.2.4 Bileşenlerine ayırma, paralelkenar yöntemi | Römorkör bölümleri (14–16): Ayır ve Birlikte | Oynanır |
| FİZ.9.2.6 Konum, alınan yol, yer değiştirme | Her bölüm sonunda alınan yol ve yer değiştirme; denizdeki noktalı bileşke oku | Kısmen |
| FİZ.9.2.6 Sürat, hız, ortalama ve anlık değerler | "Yeşil Dalga" bölümleri: trafik ışıklarına sürat ayarı, ortalama hız koridoru | Planlandı |
| FİZ.9.2.1, 9.2.2, 9.2.5, 9.2.7 Sınıflandırmalar | Bölüm aralarında kısa ayırma oyunları: banttan gelen niceliği doğru kutuya at | Planlandı |

Program sınırlarına uyulur:

- trigonometri yok,
- yalnızca dik kartezyen koordinat sistemi,
- hareket grafiği ve ivmeli hareket hesabı yok.

Büyüklükler kareli düzlemde Pisagor'la bulunur, örneğin (3, 4) için 5 kare.

## Bölümler

| # | Kısım | Ne öğretir | Kartlar ve jetonlar |
|---|---|---|---|
| 1 | Kanal | Kart bir vektördür | (0, 4) |
| 2 | Kanal | Uç uca toplama; fazla kart | (0, 2), (0, 4), (0, 3) |
| 3 | Kanal | Zıt yönlü vektör; sıra önemli (kanaldan çıkma) | (0, 5), (0, −2), (0, 4) |
| 4 | Kanal | ×2: gerçek sayıyla çarpma | (0, 3), (0, 2); ×2 |
| 5 | Kanal | ×(−1): zıt vektör | (0, 2), (0, 6); ×(−1) |
| 6 | Kanal | ×½; uzun yol da var ama iki hamle | (0, 6), (0, 4); ×½, ×(−1) |
| 7 | Liman | Bileşke; yol 7, yer değiştirme 5 | (3, 0), (0, 4) |
| 8 | Liman | Sıra bileşkeyi değil rotayı değiştirir | (3, 0), (0, 4); kaya |
| 9 | Liman | Eğik kartların bileşenleri ayrı toplanır | (2, 1), (1, 3), (2, 0) |
| 10 | Liman | Kalan vektöre eşit toplamı bul | beş kart |
| 11 | Akıntı | Kart + akıntı = bileşke | (0, 2), (0, 2); akıntı (1, 0) |
| 12 | Akıntı | Akıntıya karşı | üç kart; akıntı (0, −1) |
| 13 | Akıntı | Akıntı ve ×2 birlikte | üç kart; ×2; akıntı (1, 0) |
| 14 | Römorkörler | Bileşenlerine ayırma | (3, 4); Ayır |
| 15 | Römorkörler | Paralelkenar yöntemi | (3, 0), (0, 3); Birlikte |
| 16 | Römorkörler | Kaptanlık sınavı | dört kart; Ayır, Birlikte, ×(−1); akıntı (0, 1) |

## Reklam (AdMob)

- **Ödüllü video:** yalnızca İpucu düğmesinde. Oyuncu kendi ister, oyunu kesmez. Reklam
  fiyatı en yüksek türdür.
- **Geçiş reklamı:**
  - yalnızca bölüm aralarında, "Sonraki bölüm"e basınca,
  - ilk 3 bölümden sonra başlar,
  - her 3 bitirilen bölümde bir,
  - iki reklam arasında en az 90 saniye.
  Bu kurallar `logic/.../AdPacing.kt` içinde, testleriyle birlikte.
- **Banner yok:** Geliri düşük, ekranı daraltır, öğretmenin sınıfta açtığı oyunda dikkat
  dağıtır.
- **Onay:** Avrupa ve benzeri yerlerde Google'ın onay formu (UMP) açılış ekranında çıkar. Onay
  gelmeden reklam istenmez. Gerekiyorsa menüde "Gizlilik ayarları" düğmesi çıkar.
- **İçerik sınırı:** Reklamlar en çok PG (ebeveyn rehberliği) düzeyinde istenir, çünkü oyun
  okulda oynanır.
- **Hedef kitle:** 9. sınıf öğrencileri 14–15 yaşındadır. Play Console'da hedef kitle 13 yaş ve
  üstü seçilir. Böylece çocuk uygulamalarına özel Aile politikası zorunlu olmaz.
- **Kimlikler:**
  - Debug sürümleri hep Google'ın test kimliklerini kullanır.
  - Gerçek kimlikler yalnızca release sürümüne Gradle özellikleriyle verilir (bkz. README).
  - Geliştirirken kendi reklamına asla tıklama; AdMob hesabı kapanabilir.

## Yol haritası

- **Yeşil Dalga (FİZ.9.2.6):**
  - Hamur arabaların süratini ayarlayıp bütün ışıklara yeşilde yetişmek.
  - Ortalama hız koridoru, kamera önünde yavaşlayan ama ortalamada hızlı gideni yakalar; anlık
    ve ortalama sürat farkı buradan öğrenilir.
  - Program, trafikteki yeşil dalgayı ve sürat cezalarını örnek veriyor.
- **Sınıflandırma oyunları (FİZ.9.2.1, 9.2.2, 9.2.5, 9.2.7):**
  - temel ve türetilmiş nicelikler,
  - skaler ve vektörel nicelikler,
  - dört temel kuvvet,
  - öteleme, dönme ve titreşim hareketi.
- **Ses:**
  - motor pıtırtısı,
  - can simidine varınca düdük,
  - kayaya yaklaşınca çıtırtı.
- **İngilizce metinler** ve dünya geneline yayın.
- **Günün bulmacası:** Çözücüyle doğrulanmış, her gün yeni bir deniz.
- **Mağaza:** simge ve ekran görüntüleri (hamur görüntüleyiciyle), gizlilik politikası sayfası,
  toyquaise.com'da proje sayfası.
- **Seri:** Macun Kalesi'nin elektrik konuları ileriki sınıfların elektrik ünitelerine oturur.
  Aynı motorla bir Toyquaise serisi olur. AdMob'un kendi uygulamalarını tanıtma reklamlarıyla
  oyunlar birbirini ücretsiz tanıtır.
