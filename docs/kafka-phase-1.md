# Kafka Phase 1

## Faz Amaci
Bu fazin amaci, Kafka entegrasyonuna dogrudan "producer bagla ve mesaj bas" diye girmek yerine uygulamanin event sinirlarini once netlestirmekti. Bu repo su anda bir Play Framework monolith'i ve todo ile ilgili asil business state degisiklikleri `TodoServiceImpl` icinde kesinlesiyor. Bu nedenle ilk adim olarak gercek Kafka baglantisi, outbox tablosu veya retry mekanizmasi degil; event modeli, publisher arayuzu ve service katmanindaki entegrasyon noktalari hazirlandi.

Bu faz sonunda sistem hala bugunku gibi calisiyor olmaliydi:
- kullanici todo olusturabilmeli
- todo guncelleyebilmeli
- todo silebilmeli
- todo tamamlayabilmeli
- email actor akisi bozulmamali

Ayni anda sistemin bir sonraki fazda outbox ve gercek Kafka producer eklemeye hazir hale gelmesi hedeflendi.

## Faz Kapsami
Bu fazda bilerek yapilanlar:
- todo event kontratlarini tanimlamak
- tum eventlerin paylasacagi ortak envelope tipi eklemek
- event uretimini tek merkezde toplamak
- service katmanina publish-ready cagrilar eklemek
- gercek Kafka yerine `NoOp` publisher ile akisi baglamak
- ilk fazi anlatan teknik dokumani eklemek

Bu fazda bilerek yapilmayanlar:
- Kafka dependency eklemek
- gercek Kafka broker'a baglanmak
- outbox tablosu eklemek
- transactional event persist yapmak
- background publisher worker yazmak
- retry, DLQ veya schema version migration davranisi eklemek

Bu ayrim onemli, cunku amac once dogru sinirlari kurmakti; altyapiyi ise sonraki faza birakiyoruz.

## Eklenen ve Degisen Yapilar

### 1. Ortak event envelope
Yeni dosya:
- `app/kafka/events/DomainEventEnvelope.scala`

Bu tip, tum todo eventleri icin ortak tasiyici kontrat gorevi goruyor. Sunlari standartlastiriyor:
- `eventId`
- `eventType`
- `eventVersion`
- `occurredAt`
- `tenantId`
- `userId`
- `entityType`
- `entityId`
- `correlationId`
- `payload`

Bu envelope sayesinde sonraki fazlarda eventleri ister outbox'a yazalim, ister Kafka'ya basalım, ayni ust seviye kontrati koruyabilecegiz.

### 2. Todo event tipleri
Yeni dosya:
- `app/kafka/events/TodoDomainEvent.scala`

Eklenen event tipleri:
- `TodoCreated`
- `TodoCompleted`
- `TodoUpdated`
- `TodoDeleted`

Bu tipler su anda hafif agirlikli case class olarak tutuluyor. Bu fazda asil degerleri, uygulamanin hangi business anlarini event saydigini acik hale getirmeleri.

### 3. Event factory
Yeni dosya:
- `app/kafka/events/TodoEventFactory.scala`

Bu sinifin gorevi:
- todo modelinden envelope uretmek
- ortak event metadata'sini tek yerde kurmak
- payload alanlarini standartlastirmak

Factory kullanmamizin nedeni event olusturma kurallarini service metodlarina yaymamak. Eger bunu service icine dagitsaydik, Faz 2 ve Faz 3'te degisiklik yapmak daha zor olurdu.

### 4. Publisher arayuzu
Yeni dosyalar:
- `app/kafka/publisher/TodoEventPublisher.scala`
- `app/kafka/publisher/NoOpTodoEventPublisher.scala`

`TodoEventPublisher`, sistemin "buradan sonra event disariya cikar" sinirini temsil ediyor.

Bu tipleri genel `services` klasoru altinda tutmak yerine ayri bir `app/kafka` modulu altina tasidik. Bunun nedeni, Kafka ile ilgili kontrat ve altyapi iskeletinin uygulamanin genel servisleriyle karismamasini saglamak.

Faz 1'de bunu gercek Kafka ile degil, `NoOpTodoEventPublisher` ile bagladik. Bunun nedeni:
- service akisini bugunden publish-ready yapmak
- ama Kafka olmayan local ortamda davranisi bozmamak
- sonraki fazda sadece implementasyonu degistirerek ilerleyebilmek

### 5. Service entegrasyonu
Degisen dosya:
- `app/services/TodoServiceImpl.scala`

Bu dosyada:
- `createTodo` sonrasi `TodoCreated`
- `updateTodo` sonrasi `TodoUpdated`
- `deleteTodo` sonrasi `TodoDeleted`
- `toggleTodo` icinde sadece `false -> true` gecisinde `TodoCompleted`

publish-ready cagrilari eklendi.

Buradaki onemli nokta su:
- Kafka ile ilgili tipler artik `TodoServiceImpl` icinde sadece import edilen bagimliliklar
- ama fiziksel olarak `services` klasorunun icine dagilmiyorlar
- boylece ileride `outbox`, `publisher`, `consumer-contract`, `serialization` gibi yapilar ayni `app/kafka` agaci altinda buyuyebilecek

Onemli karar:
- `updateTodo` icinde ayrica `TodoCompleted` uretmiyoruz
- completion semantigi yalnizca `toggleTodo` akisinda temsil ediliyor

Bu sayede completion olayi tek bir business akisina bagli kaliyor ve ileride consumer davranislarini sade tutuyor.

### 6. DI wiring
Degisen dosya:
- `app/Module.scala`

Burada `TodoEventPublisher` icin default bind olarak `NoOpTodoEventPublisher` secildi. Bu karar Faz 1 icin kritik cunku:
- app hemen acilip calisabilmeli
- Kafka altyapisi olmadan integration noktasi denenebilmeli
- fazlar ilerledikce bind degistirerek gercek publisher'a gecebilmeliyiz

### 7. Config placeholder'lari
Yeni dosya:
- `conf/kafka-reference.conf.example`

Eklenen alanlar:
- `kafka.enabled = false`
- `kafka.bootstrapServers`
- `kafka.clientId`
- `kafka.topic.todoEvents`

Bu alanlar su anda sadece config surface olusturuyor. Faz 1'de aktif davranis degistirmiyorlar. Bunun amaci, sonraki fazlarda konfigurasyon eklerken yeni isim tartismasi yapmamak.

Bu placeholder'lari dogrudan `conf/application.conf` icine yazmak yerine ayri bir referans dosyasinda tuttuk. Boylece repoda mevcut olabilecek secret'lari yeniden commit etmeden Phase 1 konfigurasyon yuzeyini belgelemis olduk.

## Neden Bu Tasarim Secildi

### Event publish noktasi neden service katmani?
Bu projede controller katmani HTTP/form/request sorumlulugu tasiyor. Repository katmani ise veriyi DB'ye yazma/okuma ile ilgileniyor. Kafka event uretimi ikisinin de tam ortasinda, business state degisiminin kesinlestigi yerde olmali. Bu yer `TodoServiceImpl`.

Bu tercih su faydalari sagliyor:
- controller Kafka bilmez
- repository Kafka bilmez
- business olayin anlami service katmaninda toplanir
- Faz 2'de outbox eklenirken yine ayni sinir korunur

### Actor yapisi neden korunuyor?
Projede zaten actor tabanli async side effect deseni var:
- `EmailActor`
- `DueDateSchedulerActor`
- `EmailActorInitializer`

Bu nedenle Kafka iskeletini actor yapisini sokmeden eklemek daha guvenli. Faz 1'de actorlari kaldirmak veya Kafka'ya donusturmek yerine, onlari oldugu gibi biraktik. Boylece:
- mevcut mail davranisi bozulmuyor
- yeni event modeli sessizce yanina yerlesiyor
- sistem asamali migrasyona uygun kaliyor

### Neden gercek Kafka bu fazda eklenmedi?
Cunku Faz 1'in ana sorusu "mesaji nasil basariz?" degil, "hangi business olayi event olarak kabul ediyoruz?" sorusuydu. Bu soru netlesmeden producer, outbox veya consumer yazmak tasarimi tekrar tekrar degistirmeye neden olurdu.

## Event Semantigi
Bu fazda sabitlenen event anlami su sekilde:

### `TodoCreated`
Ne zaman uretilir:
- yeni todo basariyla persist edildikten sonra

Neyi temsil eder:
- kullanici icin yeni bir todo artik sistem gercegidir

### `TodoUpdated`
Ne zaman uretilir:
- mevcut todo basariyla guncellendikten sonra

Neyi temsil eder:
- title, description, due date veya genel todo state'i degismistir

Not:
- bu fazda `updateTodo` icinde completion semantigi ayri event'e ayrilmadi

### `TodoDeleted`
Ne zaman uretilir:
- soft delete islemi basariyla tamamlandiginda

Neyi temsil eder:
- todo artik aktif listeye dahil degildir

### `TodoCompleted`
Ne zaman uretilir:
- `toggleTodo` icinde `false -> true` gecisinde

Neyi temsil eder:
- kullanici todo'yu tamamlanmis duruma gecirmistir

Ozel kural:
- `true -> false` gecisinde event uretilmiyor
- `updateTodo` completion event'i uretmiyor

Bu karar Faz 1'de bilincli olarak alindi, cunku completion event'ini tek ve net bir business yoluna baglamak istedik.

## Faz Sonunda Sistem Davranisi
Bu fazdan sonra kullanici davranisinda bir fark olmamali. Uygulama hala:
- email actor ile bildirim gonderebilmeli
- todo CRUD akislarini calistirabilmeli
- mevcut controller ve repository yapisini korumali

Eklenen event publish cagrilari `NoOp` oldugu icin:
- Kafka yoksa hata olusmaz
- mevcut akis bozulmaz
- sonraki fazlar icin entegrasyon noktasi hazir olur

## Testler ve Dogrulama
Bu faz icin sadece kod eklemek yeterli degildi; faz sonunda degisikligin gercekten ayakta oldugunu dogrulamak gerekiyordu.

### Eklenen testler

#### `test/events/TodoEventFactorySpec.scala`
Bu testler sunlari kontrol ediyor:
- `TodoCreated` envelope olusuyor mu
- ortak kontrat alanlari dogru doluyor mu
- `tenantId`, `userId`, `entityId`, `eventVersion` beklenen sekilde mi
- payload icindeki `todoId`, `title`, `isCompleted` gibi alanlar dogru mu
- `TodoCompleted` event'i tamamlanmis state'i dogru tasiyor mu

Bu testler Faz 1'in en onemli soyutlamasi olan event factory'nin davranisini sabitliyor.

#### `test/services/NoOpTodoEventPublisherSpec.scala`
Bu test su davranisi dogruluyor:
- publisher cagrisi basariyla donuyor mu
- `NoOp` implementasyon cagirani kirmadan akisi surdurebiliyor mu

Bu testin degeri su: Faz 1'de gercek Kafka yokken bile service publish noktalarinin akisi bozmamasi gerekiyor.

### Calistirilan dogrulama komutu
Faz 1 sonunda su komut calistirildi:

```powershell
sbt test
```

### Test sonucu
Sonuc:
- tum testler gecti
- toplam 6 test basarili
- yeni event testleri ve mevcut `HomeControllerSpec` birlikte basarili calisti

Bu bize sunu gosteriyor:
- yeni event iskeleti derleniyor
- DI wiring uygulamayi kirmiyor
- mevcut uygulama test yuzeyi bozulmadi
- Faz 1 push edilmeye hazir durumda

## Bilinen Sinirlamalar
Bu faz bilincli olarak yarim bir Kafka entegrasyonu birakiyor. Henuz sunlar yok:
- gercek Kafka producer
- outbox tablosu
- transactional event persist
- retry/backoff
- failure kayitlari
- DLQ
- request kaynakli correlation id propagation

Bunlar eksik degil; sonraki fazlara bilincli olarak birakildi.

## Sonraki Faz Icin Hazirliklar
Faz 2'de bir sonraki adim, eventleri dogrudan publisher'a vermek yerine outbox tablosuna yazmak olacak. Faz 1'de eklenen yapilar bu gecisi kolaylastiriyor:
- event contract artik sabit
- service publish siniri belli
- publisher abstraction hazir
- config surface olusmus durumda

Boylece Faz 2'de asil odak su olacak:
- todo degisikligi ve event kaydini ayni transaction icine almak
- event kaybini onlemek
- sonraki fazdaki gercek Kafka publish icin saglam zemin kurmak
