# Macun Kalesi: tasarım belgesi

İngilizce adı **Tooth Fort**. Toyquaise'in öğretici oyunlarından biri: şekerler dişe saldırır, oyuncu
diş macunu makineleriyle dişi korur. Makineler elektrikle çalışır ve elektrik gerçekten hesaplanır.

## Neden bu fikir

- Şekerle diş macununun çatışmasını herkes anında anlar. Konunun diş sağlığıyla doğal bir bağı
  var; veliler bunu sever.
- Oyun hamuru görünümüyle çok iyi gider: çizgili macun kıvrımları, jelibon ayılar, lolipoplar
  hamurdan yapılınca harika görünür.
- Elektrik, ders gibi değil, daha güçlü makineyi çalıştırmanın yolu olarak öğrenilir.

## Elektrik savaşta nasıl çalışır

Her makinenin bir çalışma gerilimi var: diş fırçası 3 V, macun topu 6 V, beyazlatıcı lazer 12 V.

- **Gerilim yetmezse** makine güçsüz kalır: yavaş ve zayıf sıkar. Oyunda makinenin gücü
  gerilimin karesiyle orantılıdır (P = V²/R). Gerilimin yarısında makine gücünün dörtte birini
  verir, %35'in altında hiç çalışmaz.
- **Fazla gerilim** verirsen makine aşırı ısınır ve yanar. Sigorta atabilir.
- **Pilleri seri bağlarsan** gerilim artar ve güçlü makineyi çalıştırırsın. Oyunda iki pil uç uca
  (+ ile −) konunca birbirine değer ve seri bağlanır.
- **Paralel bağlarsan** gerilim aynı kalır ama piller daha uzun dayanır. Uzun bölümlerde bu
  önemli.
- **İnce kablo** her karede gerilim kaybettirir ve çok akımda erir. **Kalın kablo** daha
  pahalıdır. Oyun boyunca karar hep aynı: bütçe mi, güç mü, menzil mi?

### Uyarı 1: elektrik bir para birimine dönüşmemeli

Arkada küçük bir devre çözücü var (`logic/.../circuit/Network.kt`). Kirchhoff'un akım yasası her
düğümde bir denklem verir. Denklemler düğüm analiziyle (G·v = i) ve Gauss eliminasyonuyla çözülür.

- Pil, iç direnci olan bir gerilim kaynağıdır (0,25 Ω). Kısa devrede akım büyür ama sonludur.
- Kablo, kare başına bir dirençtir (ince 0,12 Ω, kalın 0,03 Ω).
- Makine, değeri anma gerilim ve gücünden gelen bir dirençtir.
- Anahtar ve sigorta çok küçük dirençlerdir; açılınca ya da atınca devreden çıkarlar.

Seri ile paralel arasındaki seçim, kablo kalınlığı ve ısınma bu yüzden gerçek stratejik
kararlardır. Ölçüm etiketleri her makinenin üstünde gerçek gerilimi gösterir.

### Uyarı 2: telefonda kablo çekmek zahmetli olabilir

Kablolar ızgaraya oturur ve parmakla tek hareketle çekilir: kablo aracını seçip kareler üzerinde
sürüklemek yeter. Parmak kare atlarsa aradaki kareler kendiliğinden doldurulur. Kablolar şeker
yolunun üstünden geçebilir (ileride kola ve sakız bunu tehlikeli kılacak). Bir karede buluşan
kablolar birleşir.

Her parçanın iki ucu vardır: + bir yanda, − karşı yanda. Kablo bir parçaya yalnızca bu iki
yandan bağlanır. Seçili parçaya yeniden dokunmak parçayı döndürür. Makinelerin uçları sarıdır
(kutupsuzdur); pilin + ucu mercan, − ucu kömür rengidir.

## Bölüm geçtikçe açılanlar

| Sıra | Açılan | Oyundaki etkisi | Müfredattaki karşılığı | Durum |
|---|---|---|---|---|
| 1 | Pil, kablo, ilk fırça | Tek makineyi çalıştırırsın | Basit devre (ilkokul) | Oynanır |
| 2 | Seri ve paralel pil, macun topu | Güçlü makineye gerilim mi, uzun savaşa dayanıklılık mı? | Üreteçlerin bağlanması | Oynanır |
| 3 | Kalın kablo, anahtar, lazer | Uzak hatlar, makineleri açıp kapatarak pil tasarrufu | Direnç, açık ve kapalı devre | Oynanır |
| 4 | Sigorta, direnç | Küçük makineyi yüksek gerilimden korursun | Ohm yasası | Parçalar hazır, bölüm yok |
| 5 | Kondansatör | Yükü biriktirir, tek seferde dev bir macun atışı yapar | Sığa (11. sınıf) | Planlandı |
| 6 | Jeneratör (pedal, rüzgâr) | Pil bitmez, ama makine ekledikçe çevirmek zorlaşır | İndüksiyon, Lenz | Planlandı |
| 7 | Transformatör, alternatif akım | Uzak cephelere yüksek gerilimle güç taşırsın | Transformatör (11. sınıf) | Planlandı |
| 8 | Diyot, LED | Akımın geri kaçmasını önler, ucuz ışık tuzakları kurarsın | Yarı iletkenler (12. sınıf) | Planlandı |
| 9 | Transistör | Algılayıcı şeker görünce kuleyi açar; küçük sinyal büyük makineyi tetikler | Transistör (12. sınıf) | Planlandı |

Güçlü makineler daha çok gerilim ve akım ister. Bu da oyuncuyu kendiliğinden seri pile, kalın
kabloya ve transformatöre iter.

Kondansatör, çözücüye geri Euler "eşlik modeli" olarak eklenecek (C/dt iletkenlik ve önceki
gerilimden gelen bir akım). Diyot ve transistör doğrusal olmadığından her adımda birkaç Newton
yinelemesi gerekecek. Çözücünün yapısı ikisine de hazır.

## Düşmanlar

| Düşman | Davranış | Durum |
|---|---|---|
| Şeker küpü | Hızlı, zayıf, kalabalık gelir | Oynanır |
| Jelibon ayı | Yavaş, dayanıklı | Oynanır |
| Lolipop | Bölüm sonu canavarı; ileride yalnızca kondansatörlerin aynı anda boşalmasıyla yenilecek | Oynanır (şimdilik çok canlı bir düşman) |
| Kola şekeri | Yere yapışkan, iletken bir sıvı döker. Kabloların üstünden geçerse kısa devre yaptırır, sigortasız hat yanar | Planlandı |
| Sakız | Kabloları kemirip koparır ve devreyi açar | Planlandı |
| Teneke şeker kutusu | Çeliktir; elektromıknatıslı kule onu yavaşlatır ya da çeker | Planlandı |

## Zaman ve para

Dalga sürerken zaman akar: piller boşalır, makineler ısınır, kablolar erir. Dalgalar arasında
zaman durur. Devre yine çözülür ve bütün ölçümler okunur, ama hiçbir şey harcanmaz. Böylece
oyuncu dalgayı başlatmadan önce gerilimleri görerek dener.

Kurarken her şey parasız geri alınır (tam iade). Dalga sırasında satılan parça yarı fiyatına
gider; yanan makine ve eriyen kablo para etmez. Biten pil, kalan yükü oranında daha ucuza
yenilenir.

## Görünüm

Görsel dil toyquaise.com'dan gelir: nane yeşili masada oyun hamuru.

- **Renkler:** sitenin hamur renkleri (`art/scenes.mjs` içindeki `clayColors`). Turkuaz, petrol,
  sarı, mercan, pembe, mor, krem, kömür.
- **Yazı:** başlık ve düğmelerde DynaPuff, metinde Lexend (okuma akıcılığı için tasarlandı).
- **Hamur gölgelendiricisi:** sitenin SVG "clay" filtresinin 3D karşılığıdır.
  - Gürültüyle hafifçe bozulmuş yüzeyler.
  - Geniş, yumuşak, mat ışık ve düşük, geniş bir parlama.
  - İnce parmak izi dokusu ve biraz düzensiz renk.
  - Turkuaza çalan yumuşak gölgeler.
  - Karakterlerin hamuru saniyede sekiz kez kıpırdar, stop-motion hissi verir.
- **Şekiller:** sitenin çizimleri gibi, her model top, yuvarlatılmış kutu, döndürülmüş şekil ve
  rulodan (yılan) yoğrulur. Dosya ya da model indirilmez; her şey kodla üretilir.

## Adlar

Macun Kalesi (Türkçe), Tooth Fort (İngilizce). Diş Kalesi de düşünüldü. Paket kimliği
`com.toyquaise.toothfort`; Google Play'e ilk yüklemeden sonra değiştirilemez.
