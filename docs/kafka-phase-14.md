# Kafka Phase 14

## Fazin Amaci
Bu fazin amaci outbox replay tarafini operasyon ekibi icin gercekten kullanisli hale getirmekti.

Faz 8'de tek bir `FAILED` outbox eventini tekrar `PENDING` yapabiliyorduk. Bu kotu degildi ama gercek hayatta yetersiz kalir. Cunku sorunlar genelde tek bir eventte degil, bir grup eventte olur:

- belli bir saat araliginda uretilen eventler bozulmus olabilir
- sadece `TodoCreated` eventleri bir sure publish edilememis olabilir
- ayni tenant altinda onlarca failed event birikmis olabilir
- operasyon ekibi "tek tek tiklayarak" degil, filtreli ve guvenli toplu replay yapmak ister

Bu yuzden Faz 14'te replay ozelligini "tek event replay" seviyesinden alip "filtreli toplu replay + replay gecmisi + replay metadata" seviyesine tasidik.

Kisa ve basit ozet:

- artik sadece tek bir failed event degil, bir grup failed event de replay edilebiliyor
- bu replay `eventType` ve `createdAt` araligina gore filtrelenebiliyor
- replay edilen eventlerin outbox satirinda iz birakiliyor
- replay yapildiginda ayri bir replay log tablosuna kayit dusuluyor
- admin endpoint'leri bu yeni operasyonlari disari aciyor

Bu faz, Kafka tarafinda "mesaji tekrar kuyruğa alalim" demekten daha fazlasini yapiyor. Artik bunu kontrollu, izlenebilir ve sonradan denetlenebilir sekilde yapiyoruz.

---

## Fazdan Once Neredeydik?
Faz 13 sonuna geldigimizde sistem sunlari yapiyordu:

- todo eventleri service katmaninda uretiliyordu
- eventler outbox tablosuna yaziliyordu
- worker pending outbox satirlarini Kafka'ya publish ediyordu
- publish hatalarinda retry ve `FAILED` durumu vardi
- admin endpoint'leri ile:
  - outbox summary alinabiliyordu
  - failed event listesi gorulebiliyordu
  - tek bir failed event replay edilebiliyordu

Yani elimizde temel operasyon yetenegi vardi.

Ama eksik olan sey suydu:

"Bir tenant altindaki su tipte failed eventleri topluca geri kuyruğa al" diyemiyorduk.

Bir diger eksik de suydu:

"Bu event daha once kac kere replay edildi, en son kim replay etti, replay bulk mu yoksa single mi yapildi?" sorularina cevap veremiyorduk.

Kisacasi:

- replay vardi
- ama replay'in kaydi yoktu
- replay filtreli degildi
- replay toplu degildi
- replay operasyonu sonradan audit edilemiyordu

Faz 14 tam olarak bu boslugu kapatir.

---

## Bu Fazda Ne Yaptik?

### 1. Outbox Satirina Replay Metadata Ekledik
`todo_event_outbox` tablosuna su alanlari ekledik:

- `replay_count`
- `last_replayed_at`
- `last_replayed_by_user_id`

Bu alanlarin anlami:

- `replay_count`: Bu outbox event kac kez tekrar kuyruğa alindi?
- `last_replayed_at`: En son ne zaman replay edildi?
- `last_replayed_by_user_id`: En son hangi admin/operasyon kullanicisi replay tetikledi?

Bu sayede artik sadece status degisimi gormuyoruz. Eventin operasyonel gecmisini de gorebiliyoruz.

Ornek:

- bir event `FAILED` oldu
- admin replay yapti
- worker publish etti ve event tekrar `PUBLISHED` oldu

Bu durumda satir artik sadece `PUBLISHED` yazmakla kalmiyor, ayni zamanda:

- `replay_count = 1`
- `last_replayed_at = ...`
- `last_replayed_by_user_id = ...`

bilgilerini de tasiyor.

Bu, gercek hayatta cok onemlidir. Cunku sadece "su an ne durumda?" degil, "buraya nasil geldi?" sorusu da onemlidir.

---

### 2. Replay Log Tablosu Ekledik
Tek basina `replay_count` yetmez. Cunku bazen bir event bir kez replay edilir, bazen de bir batch icinde tekrar kuyruğa alinmistir.

Bu farki gormek icin yeni bir tablo ekledik:

- `todo_event_outbox_replay_log`

Bu tabloya her replay aksiyonunda log dusuyor.

Log satirinda su bilgiler var:

- `outbox_id`
- `tenant_id`
- `requested_by_user_id`
- `event_type`
- `replay_mode` (`SINGLE` veya `BULK`)
- `filter_summary`
- `replayed_at`

Bu ne ise yariyor?

Ornek senaryo:

- admin sadece `TodoCreated` eventlerini replay etti
- sonra da tek bir `TodoCompleted` eventini manuel replay etti

Bu durumda log tablosunda sunu gorebiliriz:

- iki `TodoCreated` icin `BULK`
- bir `TodoCompleted` icin `SINGLE`

Yani artik replay operasyonu gecmise donuk olarak takip edilebilir.

Bu kisim gercek hayatta cok degerli. Cunku sistemde bir anomali oldugunda "kim, neyi, hangi filtreyle replay etti?" diye bakmak gerekir.

---

### 3. Bulk Replay Ekledik
Bu fazin en buyuk davranissal kazanimi budur.

Yeni davranis:

- admin sadece tek bir outbox id vermez
- isterse filtre verir
- sistem bu filtreye uyan `FAILED` eventleri secip toplu replay eder

Su filtreler destekleniyor:

- `eventType`
- `createdFrom`
- `createdTo`

Yani su gibi replay cagrilari yapabiliyoruz:

- sadece `TodoCreated` eventlerini replay et
- sadece belirli tarih araligindaki failed eventleri replay et
- hem `eventType` hem tarih araligi ile daralt

Bu davranis admin endpoint ile disa acildi:

- `POST /admin/outbox/replay`

Ornek mantik:

1. tenant icindeki failed eventleri bul
2. filtreye uyanlari sec
3. bunlari `PENDING` durumuna al
4. replay metadata alanlarini guncelle
5. replay log tablosuna log dus
6. worker bir sonraki turda bunlari tekrar Kafka'ya publish etsin

Yani admin dogrudan Kafka'ya mesaj basmiyor.
Hala dogru mimari korunuyor:

- admin operasyonu outbox durumunu degistiriyor
- worker publish ediyor

Bu onemli. Cunku replay mantigini da mevcut outbox akisina sadik kildik. Yanlis yapiya kaymadik.

---

### 4. Failed Listeleme Endpoint'ini Filtreli Hale Getirdik
Eski endpoint:

- `GET /admin/outbox/failed`

hala var, ama artik filtre kabul ediyor:

- `eventType`
- `createdFrom`
- `createdTo`

Bu ne demek?

Operasyon ekibi once su soruyu cevaplayabiliyor:

"Ben replay etmek istedigim failed eventleri once listeleyeyim."

Bu sayede "kör replay" degil, "once listele sonra replay et" akisi oluyor.

Bu da operasyon guvenligi icin iyi bir seydir.

---

### 5. Replay Loglarini Listeleyen Endpoint Ekledik
Yeni endpoint:

- `GET /admin/outbox/replay-logs`

Bu endpoint replay log tablosunu sayfali sekilde donduruyor.

Neyi gormek icin kullanilir?

- en son hangi replay islemleri yapildi
- bulk replay mi yapildi
- single replay mi yapildi
- hangi filterSummary ile replay edildi
- hangi user replay etti

Bu endpoint ozellikle operasyon sonrasi kontrol icin onemlidir.

Bir admin sunu diyebilir:

"Ben az once sadece `TodoCreated` eventlerini replay etmistim. Log kaydi dusmus mu?"

Bu endpoint bunun cevabini verir.

---

### 6. Tekil Replay Davranisini da Daha Iyi Hale Getirdik
Tekil replay zaten vardi ama artik o da ayni replay log altyapisini kullaniyor.

Yani su endpoint:

- `POST /admin/outbox/:id/replay`

artik sadece status degistirmiyor. Ayni zamanda:

- replay metadata guncelliyor
- replay log tablosuna `SINGLE` modunda kayit dusuyor

Boylece bulk ve single replay ayni operasyonel dil icinde birlesmis oldu.

---

## Hangi Dosyalar Eklendi / Degisti?

### Yeni evolution
- [7.sql](/C:/Users/Alper/todo-play-app/conf/evolutions/default/7.sql:1)

### Yeni outbox modelleri
- [TodoOutboxReplayFilters.scala](/C:/Users/Alper/todo-play-app/app/kafka/outbox/TodoOutboxReplayFilters.scala:1)
- [TodoOutboxBulkReplayResult.scala](/C:/Users/Alper/todo-play-app/app/kafka/outbox/TodoOutboxBulkReplayResult.scala:1)
- [TodoOutboxReplayLog.scala](/C:/Users/Alper/todo-play-app/app/kafka/outbox/TodoOutboxReplayLog.scala:1)
- [TodoOutboxReplayMode.scala](/C:/Users/Alper/todo-play-app/app/kafka/outbox/TodoOutboxReplayMode.scala:1)

### Guncellenen repository katmani
- [TodoOutboxRepository.scala](/C:/Users/Alper/todo-play-app/app/kafka/outbox/TodoOutboxRepository.scala:1)
- [TodoOutboxRepositoryImpl.scala](/C:/Users/Alper/todo-play-app/app/kafka/outbox/TodoOutboxRepositoryImpl.scala:1)

### Guncellenen operasyon servisi
- [TodoOutboxOperationsService.scala](/C:/Users/Alper/todo-play-app/app/kafka/outbox/TodoOutboxOperationsService.scala:1)

### Yeni / guncellenen DTO'lar
- [OutboxBulkReplayResultResponse.scala](/C:/Users/Alper/todo-play-app/app/dtos/OutboxBulkReplayResultResponse.scala:1)
- [OutboxReplayLogResponse.scala](/C:/Users/Alper/todo-play-app/app/dtos/OutboxReplayLogResponse.scala:1)
- [OutboxReplayLogPageResponse.scala](/C:/Users/Alper/todo-play-app/app/dtos/OutboxReplayLogPageResponse.scala:1)
- [OutboxFailedEventResponse.scala](/C:/Users/Alper/todo-play-app/app/dtos/OutboxFailedEventResponse.scala:1)

### Guncellenen admin katmani
- [AdminController.scala](/C:/Users/Alper/todo-play-app/app/controllers/AdminController.scala:1)
- [AdminService.scala](/C:/Users/Alper/todo-play-app/app/services/AdminService.scala:1)
- [AdminServiceImpl.scala](/C:/Users/Alper/todo-play-app/app/services/AdminServiceImpl.scala:1)
- [routes](/C:/Users/Alper/todo-play-app/conf/routes:1)

### Guncellenen testler
- [TodoOutboxOperationsServiceSpec.scala](/C:/Users/Alper/todo-play-app/test/kafka/outbox/TodoOutboxOperationsServiceSpec.scala:1)
- [TodoOutboxMonitoringServiceSpec.scala](/C:/Users/Alper/todo-play-app/test/kafka/outbox/TodoOutboxMonitoringServiceSpec.scala:1)
- [TodoOutboxPublishServiceSpec.scala](/C:/Users/Alper/todo-play-app/test/kafka/outbox/TodoOutboxPublishServiceSpec.scala:1)
- [TodoOutboxEnvelopeFactorySpec.scala](/C:/Users/Alper/todo-play-app/test/kafka/outbox/TodoOutboxEnvelopeFactorySpec.scala:1)

### Kucuk ama onemli operasyon duzeltmesi
- [start-kafka-enabled-app.ps1](/C:/Users/Alper/todo-play-app/scripts/start-kafka-enabled-app.ps1:1)

Bu scriptte sbt argumani quote edilmediği icin lokal acilis kiriliyordu. Faz 14 canli testini yaparken bu sorun yakalandi ve duzeltildi.

---

## Yeni Admin Endpoint'leri

### 1. Filtreli failed listeleme
`GET /admin/outbox/failed`

Opsiyonel query parametreleri:

- `eventType`
- `createdFrom`
- `createdTo`
- `page`

Ornek:

```text
/admin/outbox/failed?eventType=TodoCreated&createdFrom=2026-05-29T14:09:15&createdTo=2026-05-29T14:15:15
```

### 2. Bulk replay
`POST /admin/outbox/replay`

Opsiyonel query parametreleri:

- `eventType`
- `createdFrom`
- `createdTo`

Bu endpoint filtere uyan `FAILED` eventleri topluca tekrar queue'ya alir.

### 3. Replay log listeleme
`GET /admin/outbox/replay-logs`

Bu endpoint replay gecmisini listeler.

### 4. Tekil replay
`POST /admin/outbox/:id/replay`

Bu endpoint aynen duruyor ama artik replay metadata ve replay log yaziyor.

---

## Bulk Replay Nasil Calisiyor?
Basit anlatim:

1. Admin filtre secer
2. Sistem tenant icindeki failed eventleri bulur
3. Filtreye uyanlari secer
4. Secilen eventler `PENDING` olur
5. `replay_count` artar
6. `last_replayed_at` ve `last_replayed_by_user_id` dolar
7. Replay log tablosuna kayit eklenir
8. Worker bu eventleri tekrar Kafka'ya publish eder
9. Basarili publish olunca event tekrar `PUBLISHED` olur

Yani replay operasyonu "Kafka'ya elle mesaj basma" degildir.
Replay operasyonu "outbox eventini tekrar normal publish akisina sokma" isidir.

Bu fark cok onemlidir.

Neden?

Cunku replay bile sistemin ayni guvenli publish hatti uzerinden gitmelidir.

---

## Replay Logu Neden Ayri Tabloya Yazdik?
Sadece `replay_count` tutmak yeterli olsaydi ayri tabloya gerek yoktu.

Ama su sorulari cevaplamak istiyoruz:

- replay tekil mi bulk mu yapildi?
- hangi filtreyle yapildi?
- hangi admin yapti?
- ne zaman yapti?

Bu bilgiler event satirinda tam olarak tasinmaz.

O yuzden:

- event satirinda "son durum" metadata'si tutuluyor
- replay log tablosunda ise "gecmis replay aksiyonlari" tutuluyor

Bu ayirim cok temizdir.

Bir tablo:
- eventin son replay durumunu bilir

Diger tablo:
- replay tarihcesini bilir

Bu, operasyonel gozlemlenebilirlik icin daha dogru bir tasarimdir.

---

## Neden Toplu Replay'e Limit Koyduk?
Bulk replay kodunda tek bir cagrida replay edilecek event sayisina limit koyduk.

Sebep:

- operasyon ekibi yanlis filtre yazabilir
- tum tenant altindaki binlerce event ayni anda tekrar queue'ya alinabilir
- bu da sistemde gereksiz bir ani yuk olusturabilir

Bu yuzden toplu replay "kontrollu" olsun istedik.

Bu hem teknik hem operasyonel olarak daha guvenli.

Bugun kucuk bir limit koymak, yarin buyuk bir operasyonda sistemin elden gitmesini engeller.

---

## Canli Testte Ne Yaptik?
Bu faz sadece unit test ile birakilmadi. Gercek lokal Kafka + gercek Play app + gercek admin endpoint akisiyla dogrulandi.

### 1. Otomatik test paketi
Calistirdigimiz komut:

```powershell
sbt test
```

Sonuc:

- `25/25` test gecti

Bu sayi onceki fazlardan daha yuksek cunku Faz 14 ile yeni operasyon testleri eklendi.

### 2. App ve Kafka ortami
Canli testte:

- Kafka broker acikti
- Kafka UI acikti
- Play app Kafka acik modda calisti
- evolution `7` otomatik uygulandi

DB dogrulamasinda:

- `play_evolutions` tablosunda `id = 7` goruldu
- `todo_event_outbox_replay_log` tablosu olustu
- `todo_event_outbox` icindeki replay alanlari olustu

### 3. Gercek admin kullanicisi ile test
Canli testte yeni bir admin kullanicisi uretildi:

- email: `phase14.admin.1780063814@example.com`
- user id: `6C82325D-2BD8-4B0B-935A-DD84C3CA7DA5`
- tenant id: `93416698-B138-418B-9CAA-961676234EF4`

### 4. Gercek todo akisi
Iki yeni todo olusturuldu:

- `Phase14 Replay A 1780063814`
- `Phase14 Replay B 1780063814`

Sonra ilk todo complete edildi.

Bu akistan su eventler olustu:

- iki adet `TodoCreated`
- bir adet `TodoCompleted`

### 5. Replay testi icin kontrollu hata simulasyonu
Bu eventlerin ilgili outbox satirlari DB tarafinda bilerek `FAILED` yapildi.

Amac:

- gercek operasyon senaryosu yaratmak
- "failed event birikti, replay edecegiz" durumunu canlandirmak

### 6. Bulk replay testi
Admin endpoint uzerinden su filtreyle bulk replay yapildi:

- `eventType = TodoCreated`
- `createdFrom = 2026-05-29T14:09:15`
- `createdTo = 2026-05-29T14:15:15`

Beklenen sey:

- sadece iki `TodoCreated` event replay edilsin
- `TodoCompleted` event secilmesin

Canli sonuc:

- iki `TodoCreated` event replay edildi
- bunlar tekrar `PUBLISHED` oldu
- `replay_count = 1` oldu
- `last_replayed_by_user_id = 6C82325D-2BD8-4B0B-935A-DD84C3CA7DA5`
- `TodoCompleted` event bu asamada hala `FAILED` kaldi

Bu tam olarak istedigimiz davranisti gosterir:

filtre gercekten calisiyor.

### 7. Replay log testi
Bulk replay sonrasi replay log tablosunda su kayitlar goruldu:

- `A95C3C8B-8C44-4525-9781-AB6A29CF4EBD | TodoCreated | BULK`
- `4D6BE962-B517-4E49-BCCC-6A1D799A6108 | TodoCreated | BULK`

Ve `filter_summary` icinde su bilgi yaziyordu:

`eventType=TodoCreated, createdFrom=2026-05-29 14:09:15, createdTo=2026-05-29 14:15:15`

Bu logun varligi, replay operasyonunun sonradan denetlenebildigini gosterir.

### 8. Tekil replay testi
Sonra elde kalan `TodoCompleted` event icin tekil replay yapildi:

- outbox id: `C833E5DD-BB24-4473-8059-EB5E0DCA6E1A`

Canli sonuc:

- event tekrar queue'ya alindi
- worker publish etti
- event tekrar `PUBLISHED` oldu
- `replay_count = 1` oldu
- replay log tablosuna `SINGLE` modunda kayit dustu

### 9. Final DB durumu
Canli test sonunda:

- ilgili 3 replay test eventinin status'u `PUBLISHED`
- tenant icin `FAILED` sayisi `0`
- replay log sayisi `3`
  - `2 BULK`
  - `1 SINGLE`

Bu tam olarak Faz 14'un kabul kriterini saglar.

### 10. Kafka topic dogrulamasi
`todo.events.v1` topic'i okundugunda Phase 14 eventleri goruldu.

Daha da onemlisi, replay edilen eventler topic'te tekrar goruldu:

- ayni `TodoCreated` eventleri ikinci kez
- ayni `TodoCompleted` eventi ikinci kez

Bu bize sunu gosterdi:

- replay sadece DB status degisikligi degil
- gercekten yeniden Kafka publish'i tetikliyor

Bu da Faz 14 acisindan en onemli kanittir.

---

## Bu Fazda Neyi Bilerek Yapmadik?
Her sey bu fazda bitmedi. Bilerek sonraya biraktigimiz seyler var:

- toplu replay icin daha zengin filtreler
  - `aggregateId`
  - `userId`
  - `status reason`
- replay islemleri icin UI ekranlari
- replay oncesi "preview mode" ya da "dry-run"
- replay sonucunda ayrintili operasyon audit'i
- bulk replay icin daha gelismis paging ve batching stratejileri

Bunlar faydali olur ama Faz 14'un ana amaci icin zorunlu degildi.

---

## Bu Fazin Mimari Kazanimi Ne?
Bu fazla beraber sistem su noktaya geldi:

- event publish hattimiz vardi
- failed eventleri gorebiliyorduk
- ama operasyon ekibi bunlari yonetmekte kisitliydi

Faz 14 sonrasinda ise:

- failed eventleri filtreleyebiliyoruz
- topluca replay edebiliyoruz
- replay metadata tutuyoruz
- replay tarihcesi tutuyoruz
- replay operasyonunu API uzerinden yonetebiliyoruz
- replay'in gercekten Kafka publish'e donustugunu canli gorduk

Kisacasi bu faz, sistemin "sadece calisan" degil, "isletilebilir" olmasi yonunde atilmis cok onemli bir adim oldu.

Bu tam olarak operasyonel olgunluk artisi demektir.

---

## Faz 14 Sonucu
Bu fazin sonunda su cümleyi rahatça soyleyebiliyoruz:

"Operasyon ekibi failed outbox eventleri guvenli sekilde filtreleyip tekrar kuyruğa alabilir; sistem bu replay islemlerini hem kalici metadata ile hem replay logu ile izlenebilir sekilde kaydeder."

Bu, Faz 14'un hedefledigi asil kazanimin ta kendisidir.
