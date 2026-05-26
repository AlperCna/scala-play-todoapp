# Kafka Phase 3

## Bu Fazda Ne Problemi Cozmeye Calistik?
Faz 1'de event dilini kurduk.
Faz 2'de event'i kaybetmeden DB'de tutmayi ogrendik.

Ama hala su eksikti:
- event var
- outbox'ta duruyor
- fakat Kafka'ya gonderecek gercek producer kodu yok

Bu da su anlama gelir:
- eventleri sakliyoruz
- ama henuz Kafka mesaji uretemiyoruz

Faz 3'te yaptigimiz sey tam olarak bu boslugu kapatmak:

"Bu event bir gun Kafka'ya gidecekse, nasil bir producer ile gidecek?"

Burada hala worker yazmadik. Yani sistem otomatik publish etmiyor. Ama artik publish edecek altyapi siniflari var.

## Faz 3'te Neyi Ekledik?
Bu fazda Kafka producer tarafini parcalara ayirdik:

### 1. Producer ayarlari
Yeni sinif:
- `KafkaProducerSettings`
- `KafkaProducerSettingsLoader`

Bu katman ne yapiyor?
- config'ten Kafka ayarlarini okuyor
- uygulamanin guvenli default'larini veriyor

Okunan ana ayarlar:
- `enabled`
- `bootstrapServers`
- `clientId`
- `topic.todoEvents`
- `acks`
- `enableIdempotence`
- `requestTimeoutMs`
- `deliveryTimeoutMs`
- `maxInFlightRequestsPerConnection`

Yani producer kodu artik config'e bagli ama config stringlerini her yerde elle okumuyor.

### 2. Kafka record factory
Yeni sinif:
- `KafkaTodoEventRecordFactory`

Bu sinifin gorevi:
- `DomainEventEnvelope` alir
- bunu Kafka `ProducerRecord` nesnesine cevirir

Burada karar verdigimiz seyler:
- topic = `todo.events.v1`
- key = `tenantId:entityId`
- value = tum envelope'u JSON olarak yaz
- header'lara event metadata koy

Header'a koydugumuz bilgiler:
- `eventType`
- `eventVersion`
- `tenantId`
- `userId`
- `entityType`
- varsa `correlationId`

Bu cok onemli cunku ileride consumer tarafinda mesajin ne oldugunu sadece payload'a bakmadan da anlayabilecegiz.

### 3. Gercek producer client
Yeni sinif:
- `KafkaProducerClient`
- `DefaultKafkaProducerClient`

Bu katmanin gorevi:
- Kafka Java client'i olusturmak
- `send` yapmak
- sonucu `Future[Unit]` olarak dondurmek

Burada yaptigimiz sey, Kafka client'i dogrudan her yerde kullanmak yerine onu ayri bir adapter ile sarmak oldu. Bunun faydasi:
- test etmek daha kolay
- worker gelince ayni client tekrar kullanilabilir
- producer detaylari tek yerde toplanir

### 4. Gercek Kafka publisher
Yeni sinif:
- `KafkaTodoEventPublisher`

Bu katman su isi yapiyor:
- event alir
- settings'i okur
- record'u olusturur
- Kafka client ile gonderir

Bu, Faz 1'deki `TodoEventPublisher` arayuzunun gercek implementasyonu oldu.

### 5. Config'e gore secim yapan publisher
Yeni sinif:
- `ConfigurableTodoEventPublisher`

Bu katman sunu yapiyor:
- `kafka.enabled = false` ise `NoOpTodoEventPublisher`
- `kafka.enabled = true` ise `KafkaTodoEventPublisher`

Yani ayni interface korunuyor ama hangi implementasyonun kullanilacagi config ile belirleniyor.

Bu sayede:
- localde Kafka yokken uygulama yine calisabilir
- Kafka acildiginda ayni interface gercek producer'a dondurulebilir

### 6. Module wiring
`Module.scala` guncellendi.

Burada artik:
- `TodoEventPublisher` -> `ConfigurableTodoEventPublisher`
- `KafkaProducerClient` -> `DefaultKafkaProducerClient`

baglantilari eklendi.

Bu su demek:
- uygulama DI seviyesinde gercek producer altyapisini taniyor
- ama worker olmadigi icin henuz aktif publish akisi baslamis degil

### 7. Reference config genisletildi
`conf/kafka-reference.conf.example` dosyasina producer ayarlari eklendi.

Boylece sonraki fazlara girerken config isimleri de netlesmis oldu.

## Teoride Burada Neyi Ogreniyoruz?
Faz 3 biraz daha "Kafka nasil konusur?" fazi.

Buradaki onemli kavramlar:

### `acks`
Producer bir mesaji gonderdiginde ne kadar garanti istedigimizi belirler.

Burada guvenli default olarak:
- `acks = all`

Bu su anlama gelir:
- lider broker mesajı tek basina kabul etsin yetmez
- ISR tarafinda gereken onay gelsin

Bu, mesaj kaybi riskini azaltir.

### `enable.idempotence`
Idempotent producer ayni produce denemesinin bazi tekrar durumlarinda duplicate etkisini azaltmaya yardim eder.

Bu ayari acik tutmak iyi bir default'tur:
- `enableIdempotence = true`

Bu ayar duplicate problemine tam cozum degildir ama guvenilir producer davranisi icin degerlidir.

### `max.in.flight.requests.per.connection`
Producer ayni anda kac request'in yolda olabilecegini etkiler.

Bu ayari:
- `5`

olarak tuttuk. Idempotent producer ile uyumlu ve guvenli tarafta bir secim.

### Partition key neden `tenantId:entityId`?
Bizim eventlerimizde ayni todo icin sirayi korumak istiyoruz.

Bu nedenle key'i:
- `tenantId:entityId`

yaptik. Boylece:
- ayni todo ayni partition'a dusme egilimine girer
- o entity bazinda ordering daha anlamli olur

Bu global ordering saglamaz.
Ama ayni aggregate icin mantikli bir ordering zemini kurar.

## Faz 3'ten Sonra Sistem Nasil Duruyor?
Su an tablo su:

- Faz 1: event tanimi var
- Faz 2: event DB'de outbox olarak saklaniyor
- Faz 3: bu eventi Kafka'ya nasil gonderecegimizi bilen producer siniflari var
- Faz 4: henuz yok, yani outbox'taki kayitlari otomatik okuyup gonderen worker yok

Yani sistem su an:
- Kafka'ya hazir producer koduna sahip
- ama bunu otomatik kullanan runtime publish akisina henuz sahip degil

Bu gayet bilincli bir durum.

## Testte Neyi Kanitladik?

### 1. Settings loader testi
Eklenen test:
- `KafkaProducerSettingsLoaderSpec`

Bu test neyi kanitliyor?
- explicit config verilirse dogru okunuyor mu
- config yoksa guvenli default'lar geliyor mu

Bu test onemli cunku producer konfigurasyonu hataliysa ileride sorunlar sessizce cikabilir.

### 2. Record factory testi
Eklenen test:
- `KafkaTodoEventRecordFactorySpec`

Bu test neyi kanitliyor?
- topic dogru mu
- key dogru mu
- value JSON dogru olusuyor mu
- header'lar dogru yaziliyor mu

Yani event'in Kafka mesajina donusme sekli kod seviyesinde sabitlendi.

### 3. Genel test kosumu
Faz 2'den gelen outbox testleri ve Faz 1 testleri de tekrar kosuldu.

Komut:

```powershell
sbt test
```

Sonuc:
- tum testler gecti

Bu da su guveni veriyor:
- yeni producer kodu derleniyor
- eski fazlari bozmadı
- outbox + producer birlikte ayni projede uyumlu

## Bu Fazda Neyi Bilerek Yapmadik?
Hala sunlar yok:
- outbox worker
- `PENDING` kayitlari tarayip publish etme
- publish sonrasi `PUBLISHED` update etme
- retry loop
- DLQ davranisi

Yani producer hazir ama otomatik publish mekanizmasi henuz yok.

## Faz 4 Neden Gerekiyor?
Faz 4'te artik outbox tablosundaki `PENDING` kayitlari alip Kafka'ya basan worker'i ekleyecegiz.

Kisacasi:
- Faz 3 = "Kafka'ya nasil basarim?"
- Faz 4 = "Ne zaman, hangi kayitlari, nasil otomatik basarim?"
