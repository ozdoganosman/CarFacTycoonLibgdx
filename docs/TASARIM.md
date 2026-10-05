# Vektör Pilotu: tasarım belgesi

Toyquaise'in öğretici oyunlarından biri. Neon ışıklı bir uzayda küçük bir gemiyi portala
götürüyorsun. Elinde tek bir şey var: **5 birim yakıt**. Bu yakıtı istediğin yöne, istediğin
uzunlukta parçalar hâlinde harcıyorsun.

Konu, 9. sınıf fizik dersinin 2. ünitesi: **Kuvvet ve Hareket** (Türkiye Yüzyılı Maarif
Modeli, FİZ.9.2).

## Çekirdek fikir

- **Yakıt skalerdir:** Bir sayıdır ve yönü yoktur. Her bölümde 5 birim yakıt var.
- **Hamle vektördür:** Oyuncu gemiden parmağını sürükleyip bırakır. Çektiği vektör kareli
  düzlemin bir noktasına oturur, örneğin (2, 1). Gemi o vektör boyunca uçar.
- **Her parça kendi büyüklüğü kadar yakıt yakar:** Büyüklük Pisagor'la bulunur. (2, 1) için
  √5 ≈ 2,24 birim yanar.
- **Parçalar uç uca eklenir:** Gemi portala tam oturunca bölüm biter.
- **Asıl soru:** Kristallerin hepsini toplayıp portala 5 birimle nasıl yetişirim?
  - Dümdüz gitmek hep en ucuzudur.
  - Her sapma yakıttan yer.
  - Çapraz parçalar, köşe dönmekten ucuzdur.

## Sahne

- **Arka plan:** Koyu mor-lacivert bir uzay, yavaşça kayan üç katman yıldız ve renkli
  bulutsular.
- **Harita:** Holografik bir kareli düzlem. Soluk çizgiler, parlayan noktalar, kenarlarda eksen
  sayıları ve köşelerde bilimkurgu çerçevesi var.
- **Nesneler:**
  - gemi: beyaz-camgöbeği, pembe kanatlı, motoru yanan küçük bir roket;
  - asteroitler: dönen, kraterli kaya parçaları;
  - kristaller: dönerek süzülen altın elmaslar;
  - portal: birbirinin tersine dönen pembe ve camgöbeği halkalar.
- **Menzil:** Geminin çevresinde kalan yakıt kadar yarıçaplı bir nokta halkası durur. Oyuncu
  nereye yetişebileceğini görür.

## Nişan alırken

- **Parça:** Gemiden parmağın altındaki noktaya kalın, altın renkli bir ok.
- **Bileşenler:** Okun x ve y bileşenleri, kesikli çizgilerle dik üçgenin iki kenarı olarak
  çizilir. Dik açı işaretlidir; "x: 2", "y: 1" yazar.
- **Büyüklük:** Okun yanında vektörü ve büyüklüğü yazar, örneğin "(2, 1) |√5 ≈ 2,24|".
- **Yakıt göstergesi:**
  - 0'dan 5'e her birimde bir çentik vardır,
  - yanacak kısım yanıp söner,
  - yanında "−2,24" yazar.
- **Toplanacak kristaller** parlar.
- **Hamle yapılamazsa** ok kırmızı olur. Yoldaki asteroit kırmızı yanar. Bırakınca ekran
  sarsılır ve nedeni yazılır.
- **Rüzgârlı bölümlerde:**
  - parçanın ucundan mor bir rüzgâr oku çıkar,
  - gerçek yol beyaz "bileşke" okuyla gösterilir,
  - varılacak yerde hayalet bir gemi durur.

## Kurallar (kesin hâli `logic/` içinde)

- **Konumlar:** Gemi, portal, kristaller ve asteroitler ızgara noktalarındadır. Harita 5 × 7
  karedir.
- **Hamle:** Sıfır olmayan tam sayılı bir vektördür. Kalan yakıttan uzun olamaz ve harita
  dışında bitemez.
- **Asteroit:** Yolun bir asteroite uzaklığı yarım kareden azsa hamle yapılamaz. Bu yüzden yan
  yana iki asteroitin arasından geçilmez. Köşe köşeye duran iki asteroitin arasından çapraz
  geçilir.
- **Kristal:** Yalnızca gemi tam üstünden geçerse toplanır.
- **Rüzgâr:** Her hamleye bedava eklenir. Yakıt yalnızca oyuncunun çektiği parça kadar yanar.
- **Yıldızlar:**
  - portala varmak 1 yıldız,
  - her kristal 1 yıldız daha,
  - en çok 3 yıldız.
- **Yakıt bitince:** 1 birimden az yakıt kalırsa artık hamle yapılamaz. "Yakıt bitti" çıkar;
  geri al ya da baştan başla.
- **Geri al ve Baştan** ücretsizdir.

**Çözücü** (`Solver.kt`) her (konum, toplanan kristaller) durumunu Dijkstra'yla tarar. En çok
kristali, sonra en az yakıtı, sonra en az hamleyi seçer. İpucu bundan gelir. Testler her bölümün
iki kristalle 5 birime sığdığını kanıtlar.

**Bölüm üreteci** (`GeneratorTest`, `GENERATE=1` ile çalışır) asteroit kalıplarından binlerce
harita üretir:

- boşluklu duvar,
- zikzak,
- küme,
- köşegen,
- dağınık.

Her haritayı çözer ve zorluğa göre sıralar. Bölümler bu adaylar arasından seçilip elle
düzeltildi.

## Kazanımlar

| Kazanım | Oyundaki karşılığı |
|---|---|
| FİZ.9.2.2 Skaler ve vektörel nicelikler | Yakıt skaler (çentikli gösterge), hamle vektör (ok) |
| FİZ.9.2.4 Uç uca ekleme | Parçalar uç uca eklenir; rota oklarla haritada kalır |
| FİZ.9.2.4 Bileşenlerine ayırma | Nişan alırken x ve y bileşenleri dik üçgenin kenarları olarak çizilir |
| FİZ.9.2.4 Bileşke vektör | Güneş rüzgârı bölümleri: parça + rüzgâr = bileşke |
| FİZ.9.2.6 Alınan yol ve yer değiştirme | Bölüm sonunda harcanan yakıt, alınan yol ve yer değiştirme; çıkıştan gemiye kesikli pembe çizgi |

Program sınırlarına uyulur:

- trigonometri yok,
- yalnızca dik kartezyen koordinatlar,
- büyüklükler Pisagor'la hesaplanır.

## Bölümler

| # | Kısım | Ne öğretir |
|---|---|---|
| 1 | Kalkış | Tek düz parça; kristaller yolun üstünde |
| 2 | Kalkış | Çapraz parça: (3, 3) = √18 ≈ 4,24 birim |
| 3 | Kalkış | Portalı geç ve dön: yol 5, yer değiştirme 3 |
| 4 | Kalkış | Düz yolun dışındaki kristaller: sapma yakıt ister |
| 5 | Kalkış | Bir adım yana, sonra uzun çapraz |
| 6 | Asteroit Kuşağı | Duvardaki tek boşluk |
| 7 | Asteroit Kuşağı | Çapraz yol kapalı, kenardan tam 5 birim |
| 8 | Asteroit Kuşağı | Yana, sonra asteroitin üstünden çapraz |
| 9 | Asteroit Kuşağı | İki duvar, alttaki boşluktan çapraz |
| 10 | Asteroit Kuşağı | Slalom |
| 11 | Güneş Rüzgârı | Rüzgâr bedava iter: 3 birim yakıtla 6 kare |
| 12 | Güneş Rüzgârı | Rüzgârla boşluktan geç |
| 13 | Güneş Rüzgârı | Batı rüzgârına göre nişan al |
| 14 | Güneş Rüzgârı | Doğu rüzgârı, tam 5 birim |
| 15 | Güneş Rüzgârı | Pilotluk sınavı |

## Reklam (AdMob)

- **Ödüllü video:** İpucu düğmesinde. Çıkmaz bir konumda reklam gösterilmez, "bir hamle geri
  al" denir. Reklam yüklenemezse ipucu yine verilir.
- **Geçiş reklamı:**
  - yalnızca bölüm aralarında,
  - ilk 3 bölümden sonra başlar,
  - her 3 bölümde bir,
  - iki reklam arasında en az 90 saniye.
  Kuralları `AdPacing.kt` içindedir.
- **Banner yok.**
- **Onay:** Avrupa için onay formu (UMP).
- **İçerik sınırı:** Reklam içeriği en çok PG düzeyinde.
- **Kimlikler:** Debug sürümlerinde hep test kimlikleri kullanılır.

## Yol haritası

- **Ses ve müzik:** motor uğultusu, kristal çınlaması, portal sesi.
- **Yeni kısımlar:**
  - kara delik (yakıt bedava ama yön büker),
  - hareketli asteroitler,
  - yakıt istasyonu.
- **Hız bölümleri (FİZ.9.2.6):** Süre ölçülür, sürat ve hız ayrımı oynanarak öğrenilir.
- **İngilizce metinler** ve dünya geneline yayın.
- **Günün haritası:** Üreteç ve çözücüyle her gün yeni bir harita.
- **Mağaza:** simge, tanıtım videosu (`--autoplay --record`), gizlilik politikası.
