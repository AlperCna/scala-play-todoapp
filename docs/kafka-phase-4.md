# Kafka Phase 4

## Bu Fazda Ne Problemi Cozmeye Calistik?
Faz 2 ile eventleri outbox'ta guvenli sekilde saklamayi ogrendik.
Faz 3 ile bunlari Kafka'ya nasil basabilecegimizi bilen producer katmanini ekledik.

Ama hala kritik bir eksik vardi:
- outbox'ta `PENDING` kayitlar var
- producer hazir
- ama bu kayitlari alip gercekten publish eden calisan bir mekanizma yok

Faz 4'te cozdugumuz sey buydu:

"Outbox'taki kayitlari arka planda kim alip Kafka'ya gonderecek?"

Cevap:
- actor tabanli bir publisher worker

Ama bunu dogrudan actor icine gommedik. Asil mantigi test edilebilir bir servis icine koyduk; actor sadece bunu periyodik cagiriyor.

## Faz 4'te Neyi Ekledik?

### 1. Worker ayarlari
Yeni siniflar:
- `TodoOutboxWorkerSettings`
- `TodoOutboxWorkerSettingsLoader`

Burada worker icin ayarlar tanimlandi:
- `pollIntervalSeconds`
- `batchSize`
- `maxAttempts`
- `initialRetryDelaySeconds`

Bu ayarlar su sorulari kontrol ediyor:
- ne kadar sik tarayacagiz
- bir kerede kac kayit isleyecegiz
- bir kaydi kac kez deneyecegiz
- hata sonrasi ne kadar bekleyecegiz

### 2. Outbox kaydindan tekrar event ureten factory
Yeni sinif:
- `TodoOutboxEnvelopeFactory`

Outbox tablosunda artik ham satir var ama Kafka producer `DomainEventEnvelope` bekliyor.
Bu nedenle outbox satirini tekrar event envelope'a ceviren bir katman ekledik.

Bu cok onemli cunku Faz 2'de sakladigimiz veri ile Faz 3'teki producer kontratini birlestiren kopru bu.

### 3. Publish servisi
Yeni sinif:
- `TodoOutboxPublishService`

Bu Faz 4'ün beyni.

Bu servis su isi yapiyor:
1. Kafka acik mi diye bakiyor
2. uygun `PENDING` kayitlari buluyor
3. her kaydi envelope'a ceviriyor
4. producer ile publish etmeye calisiyor
5. basariliysa `PUBLISHED` yapiyor
6. hata olursa `attemptCount` artiriyor
7. tekrar denenecekse `PENDING` olarak bir sonraki zamana atiyor
8. limit dolmussa `FAILED` yapiyor

Yani Faz 4 ile birlikte sistem ilk kez "eventi saklamak"tan "eventi yollamaya calismak"a geciyor.

### 4. Repository genisletmeleri
`TodoOutboxRepository` ve implementasyonu yeni yetenekler kazandi:
- `findPublishable`
- `markPublished`
- `markFailure`

Bu sayede worker:
- hangi kayitlari isleyebilecegini secebiliyor
- basarili publish sonrasi status guncelleyebiliyor
- fail oldugunda retry veya failed durumuna gecirebiliyor

### 5. Actor worker
Yeni actor:
- `TodoOutboxPublisherActor`

Bu actor:
- app acilinca basliyor
- belirli araliklarla kendine `PublishPending` mesaji gonderiyor
- her tetiklemede `TodoOutboxPublishService` cagiriyor

Buradaki onemli tasarim karari su:
- actor scheduling yapiyor
- asil business publish mantigi serviste

Boylece test etmek daha kolay oluyor.

### 6. Startup wiring
`EmailActorInitializer` icine yeni worker actor baslangici eklendi.

Bu su demek:
- uygulama ayağa kalkinca
- email actor
- due date scheduler
- outbox publisher actor

birlikte basliyor

Yani Faz 4'ten sonra outbox publish mekanizmasi uygulamanin dogal runtime parcası oldu.

### 7. Reference config genisledi
`conf/kafka-reference.conf.example` dosyasina worker ayarlari eklendi.

Boylece:
- poll interval
- batch size
- max attempts
- initial retry delay

isimleri de netlesmis oldu.

## Faz 4'te Teoride Neyi Ogreniyoruz?

### Outbox pattern'in ikinci yarisi
Faz 2 outbox pattern'in "yazma" kismiydi.
Faz 4 ise "okuma ve publish etme" kismi oldu.

Outbox pattern ancak bu iki parca birlesince gercek anlamini bulur:
- business veri + outbox ayni transaction ile yazilir
- sonra ayri bir mekanizma bunu disari publish eder

### Retry neden gerekli?
Kafka bazen gecici olarak erisilemez olabilir:
- broker gecici hata verir
- network sorunu olur
- deploy sirasinda kisa kesinti olur

Bu durumda eventi hemen `FAILED` yaparsan gecici hatayi kalici hata gibi davranmis olursun.

Bu yuzden:
- ilk hata -> tekrar dene
- hala fail -> yine dene
- belirli siniri asarsa `FAILED`

Bu, sistemin daha dayanikli olmasini saglar.

### Backoff neden gerekli?
Ayni hatali kaydi hic beklemeden tekrar tekrar denemek sistemi gereksiz zorlar.
Bu yuzden basit exponential backoff kullandik.

Ornek:
- 1. hata -> 30 sn sonra
- 2. hata -> 60 sn sonra
- 3. hata -> 120 sn sonra

Bu sayede sistem gecici problemler karsisinda daha medeni davranir.

### `PUBLISHED` ve `FAILED` ne kazandirir?
Bu durumlar operasyonel gorunurluk saglar.

Artik diyebiliyoruz ki:
- bu event henuz bekliyor
- bu event basariyla gitti
- bu event cok denendi ve pes edildi

Bu da ileride monitoring, admin ekranlari ve replay operasyonlari icin zemin olusturur.

## Faz 4'ten Sonra Sistem Nasil Duruyor?
Artik akis su:

1. kullanici todo islemi yapar
2. event outbox'a yazilir
3. worker arka planda `PENDING` kayitlari tarar
4. producer ile Kafka'ya gondermeye calisir
5. basariliysa `PUBLISHED`
6. hata olursa retry/failed akisi calisir

Bu Faz 4 ile birlikte proje ilk kez gercek publish davranisina sahip olmaya baslar.

## Testte Neyi Kanitladik?

### 1. Outbox row -> envelope cevirisi
Eklenen test:
- `TodoOutboxEnvelopeFactorySpec`

Bu test su soruya cevap veriyor:
- DB'deki outbox satirindan tekrar dogru event nesnesi kurabiliyor muyuz?

Bu cok onemli cunku worker publish ederken elinde artik service'in olusturdugu event yok; DB satiri var.

### 2. Publish service basari senaryosu
Eklenen test:
- `TodoOutboxPublishServiceSpec`

Ilk senaryo:
- pending row bulunur
- publish basarili olur
- row `PUBLISHED` olarak isaretlenir

Bu test worker'in mutlu yolunu kanitlar.

### 3. Retry senaryosu
Ayni test icinde ikinci senaryo:
- publish fail olur
- max attempt asilmamistir
- row tekrar `PENDING` kalir
- `attemptCount` artar

Bu test gecici hatalari dogru yonettigimizi gosterir.

### 4. Failed senaryosu
Ucuncu senaryo:
- publish fail olur
- max attempt sinirina gelinmistir
- row `FAILED` olur

Bu da sonsuz retry yerine kontrollu pes etme davranisini kanitlar.

### 5. Kafka disabled senaryosu
Son senaryo:
- Kafka kapaliysa servis hic publish etmez
- status degistirmez

Bu da local veya kapali ortamlarda worker'in yanlislikla kayitlari tuketmemesini saglar.

### Genel test komutu
Calistirilan komut:

```powershell
sbt test
```

Sonuc:
- tum testler gecti

Bu Faz 4 icin su guveni verir:
- worker mantigi derleniyor
- retry mantigi calisiyor
- eski fazlari bozmuyor

## Bu Fazda Neyi Bilerek Yapmadik?
Hala sunlar yok:
- DLQ topic'e gercek publish
- failed event replay araci
- monitoring dashboard
- consumer tarafi

Yani Faz 4 ile publish loop geldi ama operasyona dair daha ileri kolayliklar henuz eklenmedi.

## Faz 5 Neden Gerekiyor?
Faz 5'te odak:
- testleri daha da guclendirmek
- operasyonel dayanikliligi artirmak
- gerekirse log/metric gibi iyilestirmeleri eklemek

Kisacasi:
- Faz 4 = worker ve publish loop
- Faz 5 = saglamlastirma ve operasyonel kalite
