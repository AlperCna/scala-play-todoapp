# Kafka Phase 5

## Bu Fazda Ne Problemi Cozmeye Calistik?
Faz 4'ten sonra elimizde artik gercek bir event akisi vardi:
- business olay oluyor
- event outbox'a yaziliyor
- worker bu kaydi alip Kafka'ya gondermeye calisiyor
- basariliysa `PUBLISHED`
- hata olursa retry veya `FAILED`

Bu cok degerli bir nokta ama tek basina yeterli degil.

Cunku gercek hayatta su sorular hemen onemli hale gelir:
- sistem su an kac event isliyor
- hangileri basarili oluyor
- hangileri retry aliyor
- hangileri artik `FAILED`
- worker ayni anda iki kez calisiyor mu
- sorun ciktiginda bizim elimizde nasil bir gorunurluk var

Yani Faz 4 "sistem calisiyor mu?" sorusunu cevapliyordu.
Faz 5 ise "sistem calisirken ne yaptigini anlayabiliyor muyuz?" sorusunu cevapliyor.

Bu fark cok onemli.
Production sistemlerde en zor sey bazen kodu yazmak degil, calisirken ne yaptigini anlayabilmektir.

Bu yuzden Faz 5'in ana konusu:
- gozlemlenebilirlik
- operasyonel netlik
- overlap gibi kucuk ama can sikan runtime riskleri azaltmak
- bu davranislari test ile kilitlemek

Kisacasi:
- Faz 4 = mekanizma
- Faz 5 = mekanizmanin daha saglam ve anlasilir hale gelmesi

## Faz 5'ten Once Nasil Bir Sorun Vardi?
Faz 4'te `TodoOutboxPublishService` bir batch calistiriyordu ama bize fazla az bilgi veriyordu.

Mesela:
- "3 tane publish oldu" diyebiliyorduk

Ama bu tek basina yetmez.

Cunku su ihtiyaclar var:
- acaba toplam 10 kayit denendi de 3'u mu gitti?
- 2 tanesi retry mi aldi?
- 1 tanesi artik `FAILED` mi oldu?
- Kafka kapali oldugu icin mi hicbir sey olmadi?
- yoksa cidden hic publish edilecek kayit mi yoktu?

Bu ayrimlari gormeden, sistem bozuldugunda neyin normal neyin anormal oldugunu anlamak zorlasir.

Ayrica actor tarafinda da su risk vardi:
- scheduler yeni tick gonderir
- onceki publish batch'i henuz bitmemistir
- ikinci batch ayni anda baslar

Bu her zaman direkt veri bozar demeyiz ama:
- gereksiz cift calisma
- karisik log
- ayni anda birden fazla publish denemesi
- zor debug edilen davranislar

gibi dertler cikarabilir.

Faz 5 tam olarak bu "ince ayar ama kritik" kalite konularina odaklandi.

## Bu Fazda Tam Olarak Ne Yaptik?

### 1. Publish sonucunu sayisal olarak zenginlestirdik
Yeni sinif:
- `TodoOutboxPublishResult`

Bu sinif su bilgileri tasiyor:
- `processed`
- `published`
- `retried`
- `failed`
- `skipped`

Bu neden onemli?
Cunku artik bir batch sonunda sistemin ne yaptigini daha net okuyabiliyoruz.

Eskiden:
- "su kadar kayit publish edildi"

Simdi:
- toplam kac kayit islendigi
- kacinin basarili oldugu
- kacinin tekrar denenecegi
- kacinin artik kalici olarak `FAILED` oldugu
- Kafka kapali oldugu icin mi hic bir is yapilmadigi

ayri ayri gorulebiliyor.

Bu, operasyonel kaliteyi baya yukari ceker.

### 2. Monitoring icin ozet tipleri ekledik
Yeni siniflar:
- `TodoOutboxStatusSummary`
- `TodoOutboxMonitoringService`

Buradaki fikir su:
Outbox durum bilgisi uygulamanin baska yerlerine de lazim olabilir.

Ornek:
- yarin admin paneline "pending outbox count" gostermek isteyebiliriz
- health endpoint'e "failed outbox count" koymak isteyebiliriz
- loglara belirli periyotlarla ozet basmak isteyebiliriz

Bu nedenle outbox status'lerini tek tek repository'den ceken bir monitoring service yazdik.

Su an bu servis UI veya endpoint'e bagli degil.
Ama bu karar yine de cok degerli, cunku ileride ihtiyac duyuldugunda bu bilgi daginik halde degil, tek yerde hazir.

### 3. Publish service artik sadece is yapmiyor, sonuc da raporluyor
`TodoOutboxPublishService` Faz 5'te daha olgun hale geldi.

Simdi servis:
- batch'i calistiriyor
- her event sonucunu kategorize ediyor
- sonra bunlardan ozet bir `TodoOutboxPublishResult` uretiyor

Yani servis artik sadece "workerin arka planda kullandigi bir sey" degil.
Ayni zamanda "bu batch ne yapti?" sorusunun cevabini da veriyor.

Bu neden guzel?
Cunku servis sonucu:
- loglayabiliriz
- testte kontrol edebiliriz
- ileride metric'e cevirebiliriz
- admin ekrana tasiyabiliriz

### 4. Actor overlap korumasi ekledik
`TodoOutboxPublisherActor` icine `publishInProgress` korumasi eklendi.

Bu ne demek?
Eger actor bir batch calistirirken yeni bir tick gelirse:
- ikinci batch'i baslatmiyor
- bunun yerine warning log atiyor
- onceki batch bitince tekrar normal akis devam ediyor

Bu neden onemli?
Periyodik worker'larda overlap problemi cok yaygindir.
Ozellikle:
- publish batch uzun surerse
- scheduler kisa interval ile calisiyorsa
- yavas broker ya da network gecikmesi varsa

iki batch ust uste binebilir.

Biz burada cok basit ama etkili bir koruma ekledik.
Bu Faz 5'in "kucuk degisiklik ama davranis kalitesi yuksek" kisimlarindan biri.

### 5. Actor loglarini anlamli hale getirdik
Actor sadece "su kadar publish edildi" demiyor artik.

Simdi batch sonucu daha zengin sekilde loglanabiliyor:
- processed
- published
- retried
- failed

Bu ne kazandirir?
Logu okuyan biri:
- sistemin tamamen durdugunu
- sadece retry ile ayakta kaldigini
- bir şeylerin `FAILED` oldugunu

daha kolay fark eder.

Yani bu faz sadece kodu degil, okunabilir runtime davranisini da iyilestirdi.

## Teoride Burada Neyi Ogreniyoruz?

### Neden "calisiyor" demek yetmez?
Dagitik sistemlerde sadece "kod var" veya "mesaj gidiyor" demek yeterli degildir.
Asil onemli soru:

"Sorun ciktiginda bunu fark edebiliyor musun?"

Bu yuzden Faz 5'in teorik degeri buyuk:
- event pipeline'ini sadece kurmuyoruz
- ayni zamanda onu izlenebilir hale getiriyoruz

### Neden success count tek basina yeterli degil?
Gercek hayatta su iki durum cok farklidir:

Durum A:
- 3 mesaj islenmis
- 3'u de publish olmus

Durum B:
- 10 mesaj islenmis
- 3'u publish
- 5'i retry
- 2'si failed

Eger sadece "3 publish oldu" bilgisine bakarsan bu iki durumu ayiramazsin.

Bu nedenle Faz 5'te result tipini zenginlestirdik.

### Overlap neden risklidir?
Background worker'larda overlap bazen ciddi buglara yol acar:
- duplicate publish denemeleri
- gereksiz yuk
- state'in ayni anda guncellenmeye calisilmasi
- beklenmedik log karmasasi

Bizim mevcut yapimiz idempotent davranis ve status update mantigi sayesinde tamamen felaket uretmezdi belki, ama yine de temiz bir tasarim olmazdi.

Bu nedenle overlap'i basit bir in-progress bayragi ile engelledik.

### Monitoring neden ayri servis olarak tutuldu?
Monitoring bilgisi zamanla bircok yere lazim olur:
- dashboard
- endpoint
- scheduled health log
- alarm sistemi

Bu bilgiyi servislerin arasina dagitmak yerine ayri bir service'te toplamak:
- tekrar kullanimı artirir
- test etmeyi kolaylastirir
- kodu daha temiz tutar

Bu yuzden `TodoOutboxMonitoringService` iyi bir yatirim oldu.

## Bu Fazda Eklenen/Degisen Ana Yapi Ne Is Yapiyor?

### `TodoOutboxPublishResult`
Bu sinif, bir batch'in sonucunu ozetler.

Ogrenme notu:
Bu tipler production sistemlerde cok faydalidir cunku log ve metric mantigina dogrudan baglanabilirler.

### `TodoOutboxStatusSummary`
Bu sinif outbox tablosunun genel durumunun ozetidir.

Ogrenme notu:
Sistemleri sadece request bazli degil, "arkadaki backlog" bazli da izlemek gerekir. Bu tip onun ilk adimi.

### `TodoOutboxMonitoringService`
Outbox'taki `PENDING`, `PUBLISHED`, `FAILED` sayilarini tek yerden toplar.

Ogrenme notu:
Monitoring verisini repository'lerden tek tek cekmek yerine servis olarak toplamak, ileride buyumeyi kolaylastirir.

### `TodoOutboxPublishService`
Bu fazda daha anlamli bir sonuc dondurur hale geldi.

Ogrenme notu:
Background job'larda "void calisti" yerine "ne oldu?" sorusunun cevabini veren return type'lar cok degerlidir.

### `TodoOutboxPublisherActor`
Overlap korumasi ve batch sonucu loglamasi kazandi.

Ogrenme notu:
Scheduler kullanan actor'larda overlap dusunmek gerekir; aksi halde zaman bazli yarismalar cikar.

## Testte Neyi Kanitladik?
Bu fazda sadece "yeni dosyalar eklendi" demek yetmezdi.
Asil amac, batch sonucu ve monitoring mantiginin gercekten bekledigimiz gibi davrandigini ispatlamakti.

### 1. Publish service sonucu artik daha net test ediliyor
`TodoOutboxPublishServiceSpec` guncellendi.

Bu testler artik sadece basari senaryosunu degil, su bilgileri de dogruluyor:
- `processed`
- `published`
- `retried`
- `failed`
- `skipped`

Yani artik testlerimiz daha davranis odakli hale geldi.

Bu neyi ispatliyor?
- batch sonucunun raporlama mantigi dogru
- retry ve fail ayrimi net
- Kafka disabled senaryosu raporda da ayrisiyor

### 2. Monitoring service icin yeni test eklendi
Eklenen test:
- `TodoOutboxMonitoringServiceSpec`

Bu test su soruya cevap veriyor:
- status bazli sayilar dogru bir summary nesnesine donuyor mu?

Bu kucuk gibi gorunur ama onemlidir.
Cunku ileride backlog, health veya admin ekranina temel olacak bilgi burada.

### 3. Tum sistem testleri tekrar kosuldu
Calistirilan komut:

```powershell
sbt test
```

Sonuc:
- toplam 19 test gecti
- hata yok

Bu bize sunu gosteriyor:
- Faz 5 degisiklikleri derleniyor
- onceki fazlari bozmuyor
- publish service, monitoring ve actor sertlestirmesi birlikte uyumlu

## Faz 5'ten Sonra Sistem Hangi Seviyeye Geldi?
Bu noktada artik elimizde:
- event kontrati
- outbox persistence
- Kafka producer
- outbox worker
- retry ve fail mekanizmasi
- batch sonucu ozeti
- monitoring ozeti
- overlap korumasi

var.

Yani sistem artik sadece "bir seyler deniyor" seviyesinde degil.
Daha kontrollu, daha izlenebilir ve biraz daha production'a yakin bir event pipeline seviyesine geldi.

## Hala Ne Eksik?
Bu fazdan sonra bile hala future work olan seyler var:
- gercek DLQ topic davranisi
- failed event replay araci
- monitoring endpoint veya admin dashboard
- consumer servisleri
- gercek broker/topic ortamı
- alarm/metric entegrasyonu

Yani Faz 5'te yaptigimiz sey:
"mevcut pipeline'i kod olarak degil, operasyon kalitesi olarak olgunlastirmak" oldu.

## Faz 6 Neden Gerekli Olabilir?
Eger consumer mimarisi veya ders/workshop tarafina gecilecekse Faz 6 mantikli olur.

Orada artik su konulara odaklanilabilir:
- bu eventleri kim tuketecek
- consumer gruplari nasil calisacak
- retry/DLQ consumer tarafinda nasil dusunulecek
- bu producer tarafinin karsisinda nasil bir ekosistem kurulacak

Yani Faz 5 producer/outbox hattini olgunlastirdi.
Bir sonraki buyuk genisleme noktasi consumer ve operasyon ekosistemi olur.
