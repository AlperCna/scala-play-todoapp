# Kafka Phase 11 - Audit Consumer

## Fazın Amacı
Bu fazın amacı, `todo.events.v1` topic'indeki todo eventlerinden event-driven audit kaydı üretmekti. Producer tarafı ve notification consumer tarafı çalışıyor olsa bile, sistemin operasyonel iz bırakma tarafı hâlâ büyük ölçüde request/controller tabanlıydı. Audit consumer ile birlikte bu projede ilk kez şu soruya gerçek bir cevap vermiş olduk:

> "Todo uygulamasında gerçekleşen iş olaylarını, HTTP isteğinden bağımsız olarak Kafka eventlerinden yeniden inşa edebilir miyiz?"

Bu fazın cevabı: evet.

## Bu Fazdan Önce Sistem Neredeydi?
Faz 10 sonuna kadar elimizde şunlar vardı:

- Play uygulaması todo eventleri üretiyordu
- outbox bu eventleri güvenli şekilde tutuyordu
- worker bunları Kafka'ya publish ediyordu
- notification consumer bazı eventleri okuyup sandbox notification mantığı ile işliyordu

Ama audit tarafında hâlâ şu yapı baskındı:

- controller bir request aldıktan sonra audit log yazıyor
- yani audit üretimi HTTP request yaşam döngüsüne bağlı
- event bazlı yeniden üretilebilir bağımsız audit hattı yok

Bu yaklaşım kötü değil, ama event-driven hedef için sınırlı. Çünkü uzun vadede audit'i şu kaynaklardan biri üzerinden üretmek isteriz:

- doğrudan business eventler
- ya da projection/stream sonuçları

Request tabanlı audit bazı senaryolarda eksik kalabilir:

- başka bir servis aynı olayı üretirse ne olacak?
- aynı business event farklı giriş noktalarından gelirse?
- gelecekte consumer tabanlı iş akışları eklenirse audit nereden beslenecek?

Bu yüzden Faz 11’de audit üretimini event-driven çizgiye taşıyacak ilk consumer yazıldı.

## Fazın Hedefleri
Bu fazla hedeflediğimiz şeyler şunlardı:

- ayrı bir `todo-audit-consumer` modülü açmak
- `TodoCreated`, `TodoUpdated`, `TodoCompleted`, `TodoDeleted` eventlerini Kafka'dan gerçekten okumak
- bu eventleri `audit_logs` tablosuna çevirmek
- tenant ayrımını korumak
- duplicate eventlerin ikinci kez audit kaydı üretmesini engellemek
- offset commit'i yalnızca audit write başarılı olduktan sonra yapmak
- mevcut controller audit log yapısını hemen sökmeden paralel çalıştırmak

Bu hedefler bilerek seçildi. Çünkü audit consumer yazarken en sık yapılan iki hata şunlardır:

1. Consumer yalnızca log basar ama gerçek audit tablosuna yazmaz
2. Duplicate eventlerin tekrar gelmesi halinde audit tablosu şişer

Bu fazda özellikle ikinci maddeyi ciddiye aldık.

## Neden Ayrı Consumer Modülü?
Audit consumer ana Play uygulamasının içine gömülmedi. Ayrı modül olarak açıldı:

- [todo-audit-consumer](C:/Users/Alper/todo-play-app/consumers/todo-audit-consumer/README.md)

Bunun sebebi mimari olarak şudur:

- producer app business olaylarının sahibidir
- consumer app bu olayların türev etkilerini üretir

Audit, business write path’in içine gömülü kalmak zorunda değildir. Hatta event-driven sistemlerde audit çoğu zaman ayrı süreçte daha iyi yaşar çünkü:

- ölçeklenmesi farklı olabilir
- hata izolasyonu daha iyidir
- ileride başka event kaynaklarını da dinleyebilir
- business uygulamadan bağımsız deploy edilebilir

Bu yüzden audit consumer’ı monolith içine actor gibi eklemek yerine, ayrı süreç mantığında kurduk.

## Bu Fazda Eklenen Ana Parçalar

### 1. Audit Consumer Runtime
Ana giriş noktası:

- [AuditConsumerApp.scala](/C:/Users/Alper/todo-play-app/consumers/todo-audit-consumer/src/main/scala/com/alper/todo/auditconsumer/AuditConsumerApp.scala:1)

Bu sınıf şunları yapar:

- Kafka consumer oluşturur
- `todo.events.v1` topic'ine subscribe olur
- poll loop ile kayıtları çeker
- her mesajı handler katmanına verir
- işleme tamamlanırsa manual offset commit yapar
- shutdown sırasında consumer'ı düzgün şekilde kapatır

Bu, audit consumer'ı sadece bir kütüphane fikri olmaktan çıkarıp gerçekten çalışan bir süreç haline getirdi.

### 2. Consumer Settings ve Loader
Config modelleri:

- [AuditConsumerSettings.scala](/C:/Users/Alper/todo-play-app/consumers/todo-audit-consumer/src/main/scala/com/alper/todo/auditconsumer/config/AuditConsumerSettings.scala:1)
- [AuditConsumerSettingsLoader.scala](/C:/Users/Alper/todo-play-app/consumers/todo-audit-consumer/src/main/scala/com/alper/todo/auditconsumer/config/AuditConsumerSettingsLoader.scala:1)

Bu katman sayesinde şu bilgiler config'ten okunuyor:

- `bootstrapServers`
- `topic`
- `groupId`
- `consumerName`
- `supportedEventVersion`
- DB bağlantı ayarları

Bu önemli çünkü audit consumer artık sabit kodlanmış localhost script’i değil; kendi ayar yüzeyi olan bağımsız bir süreç.

### 3. Event Contract Parse Katmanı
JSON parse katmanı:

- [TodoEventJson.scala](/C:/Users/Alper/todo-play-app/consumers/todo-audit-consumer/src/main/scala/com/alper/todo/auditconsumer/json/TodoEventJson.scala:1)

Bu katmanın görevi:

- producer tarafından üretilen event JSON'unu parse etmek
- `TodoEventEnvelope` modeline dönüştürmek
- bozuk payload'ları business katmanına taşımadan ayırmak

Bu önemli çünkü consumer tarafında her bozuk payload audit logic seviyesinde ele alınmamalı. Parse ve processing ayrımı sistemi daha temiz ve daha güvenli yapar.

### 4. Audit Command Factory
Yeni factory:

- [AuditCommandFactory.scala](/C:/Users/Alper/todo-play-app/consumers/todo-audit-consumer/src/main/scala/com/alper/todo/auditconsumer/service/AuditCommandFactory.scala:1)

Bu sınıf event’i audit’e çevrilen iş komutuna dönüştürüyor.

Şu eventler destekleniyor:

- `TodoCreated`
- `TodoUpdated`
- `TodoCompleted`
- `TodoDeleted`

Üretilen action metinleri örnek olarak şu çizgide:

- `TODO_EVENT_CREATED: <title>`
- `TODO_EVENT_UPDATED: <title>`
- `TODO_EVENT_COMPLETED: <title>`
- `TODO_EVENT_DELETED: <title>`

Yani controller tarafındaki audit yaklaşımına benzer ama event tabanlı bir action dili oluştu.

### 5. Audit Processor
Yeni processor:

- [AuditEventProcessor.scala](/C:/Users/Alper/todo-play-app/consumers/todo-audit-consumer/src/main/scala/com/alper/todo/auditconsumer/service/AuditEventProcessor.scala:1)

Bu sınıf şu kuralları uygular:

- `eventVersion` desteklenmiyorsa ignore
- event type desteklenmiyorsa ignore
- destekleniyorsa `AuditLogWriter` çağır

Bu sınıfın rolü önemli çünkü Kafka’daki her eventin audit üreteceğini varsaymıyoruz. Önce contract ve kapsam kontrolü yapılıyor, sonra yazma kararı alınıyor.

### 6. Record Handler
Yeni handler:

- [AuditKafkaRecordHandler.scala](/C:/Users/Alper/todo-play-app/consumers/todo-audit-consumer/src/main/scala/com/alper/todo/auditconsumer/service/AuditKafkaRecordHandler.scala:1)

Görevi:

- raw Kafka record value alır
- JSON parse eder
- parse başarısızsa `MalformedPayloadIgnored`
- parse başarılıysa processor'a iletir

Bu ayrım sayesinde malformed payload geldiğinde consumer süreci patlamıyor. Bunun yerine güvenli biçimde ignore edilip log basılıyor.

## En Kritik Tasarım Kararı: Duplicate Audit Nasıl Engellendi?
Bu fazın en kritik noktası buydu.

Kafka consumer dünyasında şu durum her zaman mümkündür:

- aynı event tekrar gelir
- consumer tekrar başlar
- offset commit zamanlaması nedeniyle kayıt yeniden görülebilir
- topic’e test veya replay yüzünden event tekrar basılabilir

Audit sistemi duplicate kayıt üretirse log tablosu güvenilmez hale gelir. O yüzden burada sadece "gelen eventId’leri hafızada tutalım" demek yeterli değildi.

### Bu Yüzden Yeni Evolution Eklendi
Yeni DB evolution:

- [5.sql](/C:/Users/Alper/todo-play-app/conf/evolutions/default/5.sql:1)

Bu dosya şu tabloyu ekler:

- `consumer_processed_events`

Alanlar:

- `consumer_name`
- `event_id`
- `tenant_id`
- `processed_at`

En önemli kural:

- `(consumer_name, event_id)` primary key

Bu şu anlama gelir:

- aynı consumer aynı event’i ikinci kez işlenmiş olarak kaydedemez
- consumer bazlı idempotency mümkün olur

### Transaction Stratejisi
Audit consumer’da audit yazımı ile processed event kaydı aynı anda düşünülmeliydi. Bu yüzden JDBC writer şu akışla kuruldu:

1. DB connection aç
2. auto-commit kapat
3. event daha önce işlendi mi bak
4. işlenmediyse `audit_logs` kaydını yaz
5. aynı transaction içinde `consumer_processed_events` kaydını yaz
6. commit

Eğer event daha önce işlenmişse:

- transaction rollback
- `DuplicateIgnored`

Bu mantığı uygulayan sınıf:

- [JdbcAuditLogWriter.scala](/C:/Users/Alper/todo-play-app/consumers/todo-audit-consumer/src/main/scala/com/alper/todo/auditconsumer/infrastructure/JdbcAuditLogWriter.scala:1)

Bu tasarımın önemi çok büyük. Çünkü burada sadece duplicate fark etmek değil, audit log ile processed event kaydını atomik tutmak istedik.

## Offset Commit Mantığı
Bu fazda da notification consumer’daki prensibi koruduk:

> Offset commit yalnızca işleme başarıyla tamamlandıktan sonra yapılır.

Audit consumer mesajı:

- parse ediyor
- process ediyor
- DB’ye yazıyor
- duplicate ise güvenli şekilde ignore ediyor
- bundan sonra commit ediyor

Bu sayede en azından bu fazın tasarımında "erken commit" kaynaklı audit kaybı riskini azaltmış olduk.

## Canlıda Neleri Doğruladık?
Bu faz için sadece unit testle kalmadık. Gerçek Kafka + gerçek app + gerçek DB zinciri üzerinde doğrulama yaptık.

### 1. Consumer gerçekten ayağa kalktı
Kafka açıkken audit consumer başlatıldı ve `todo.events.v1` topic'ini dinledi.

Loglarda görüldü:

- consumer start
- partition/offset bazlı handle sonuçları

### 2. Mevcut topic’teki eventleri okudu
Daha önce üretilmiş eventleri okuyup audit üretmeye başladı.

Özellikle loglarda şu davranış görüldü:

- bazı eventler `Processed`
- daha önce aynı event tekrar gelmişse `DuplicateIgnored`

### 3. Yeni canlı todo yaşam döngüsü üretildi
App `localhost:9000` üzerinde açıldı.

Gerçek HTTP akışla:

- register
- login
- create
- update
- complete
- delete

işlemleri yapıldı.

### 4. Bu eventlerden audit kayıtları üretildi
DB sorgusunda ilgili todo için şu kayıtlar görüldü:

- `TODO_EVENT_CREATED`
- `TODO_EVENT_UPDATED`
- `TODO_EVENT_COMPLETED`
- `TODO_EVENT_DELETED`

Yani bu fazın en önemli kabul kriteri karşılandı:

> event bazlı audit kaydı gerçekten oluştu

### 5. Tenant ayrımı korundu
Aynı sorguda audit kayıtlarının tenant id bilgisi doğru taşındı.

Bu da ikinci kabul kriterinin karşılandığını gösterdi:

> tenant ayrımı korunmalı

### 6. Duplicate audit çoğalmadı
Son olarak aynı `TodoCreated` event’i topic’e ikinci kez manuel olarak tekrar basıldı.

Beklenen davranış:

- consumer bunu tekrar okuyacak
- ama ikinci audit kaydı üretmeyecek

Gerçek sonuç:

- consumer logunda `DuplicateIgnored` görüldü
- `audit_logs` içinde ilgili create kaydı sayısı `1` kaldı
- `consumer_processed_events` içinde event için tek kayıt kaldı

Bu da üçüncü kabul kriterini doğruladı:

> duplicate event audit'i çoğaltmamalı

## Testler
Bu faz için eklenen testler:

- [AuditConsumerSettingsLoaderSpec.scala](/C:/Users/Alper/todo-play-app/consumers/todo-audit-consumer/test/com/alper/todo/auditconsumer/config/AuditConsumerSettingsLoaderSpec.scala:1)
- [AuditEventProcessorSpec.scala](/C:/Users/Alper/todo-play-app/consumers/todo-audit-consumer/test/com/alper/todo/auditconsumer/service/AuditEventProcessorSpec.scala:1)
- [AuditKafkaRecordHandlerSpec.scala](/C:/Users/Alper/todo-play-app/consumers/todo-audit-consumer/test/com/alper/todo/auditconsumer/service/AuditKafkaRecordHandlerSpec.scala:1)

Doğrulananlar:

- settings loader config'i doğru okuyor mu
- supported event audit komutuna dönüşüyor mu
- unsupported event ignore ediliyor mu
- unsupported version ignore ediliyor mu
- writer duplicate sonucu döndürürse processor bunu koruyor mu
- malformed payload consumer'ı patlatmadan ignore ediliyor mu

Sonuç:

- audit consumer modülü `7/7` test geçti
- root repo `23/23` test geçti

## Evolution Konusu
Bu fazda evolution da eklendi ve önemliydi:

- [5.sql](/C:/Users/Alper/todo-play-app/conf/evolutions/default/5.sql:1)

İlk kontrol sırasında yeni tablo görünmediği için ayrıca doğrulama yapıldı. Son durumda:

- `play_evolutions` içinde `id = 5` kaydı görüldü
- `consumer_processed_events` tablosu DB’de mevcut

Yani bu fazın DB zemini de tamamlandı.

## Bu Fazda Bilerek Yapmadıklarımız
Açıkça sonraya bıraktığımız şeyler:

- controller-side audit logging'i kaldırmak
- audit consumer için admin ekranı yapmak
- audit eventlerini ayrı projection tablosuna yazmak
- consumer tarafı retry/DLQ açmak
- audit formatını zengin metadata ile genişletmek
- IP/User-Agent gibi request özel bilgileri event-driven audit'e taşımak

Bu son madde özellikle önemli: event-driven audit, request tabanlı audit ile birebir aynı veri yüzeyine sahip olmak zorunda değil. Request’ten gelen bazı alanlar event’te yoksa, audit consumer da onları üretemez. Bu normaldir.

## Teorik Olarak Ne Öğrendik?
Bu faz şu dersleri somutlaştırdı:

### Event-driven audit mümkündür
Audit sadece controller’a bağlı olmak zorunda değil. Domain eventlerden de üretilebilir.

### Duplicate kontrolü consumer dünyasında merkezi konudur
Audit gibi append-only mantıkta çalışan alanlarda duplicate eventler çok tehlikelidir. Kalıcı idempotency tablosu bu yüzden önemlidir.

### Transaction sınırı consumer tarafında da önemlidir
Producer tarafında outbox ne kadar önemliyse, consumer tarafında da "audit + processed event" beraber yazımı aynı derecede önemlidir.

### Consumer başarısı sadece “çalışıyor mu” değildir
Gerçek ölçüt şudur:

- doğru şeyi yazıyor mu
- tekrar gelen veriyi çoğaltmıyor mu
- tenant sınırını bozuyor mu

Bu fazda bunların hepsi canlı doğrulandı.

## Bu Fazdan Sonra Sistemin Yeni Durumu
Artık sistemin akışı daha güçlü:

1. Todo app event üretir
2. Event outbox’a yazılır
3. Worker Kafka’ya publish eder
4. Notification consumer eventlerden bildirim türetir
5. Audit consumer eventlerden audit kaydı türetir

Yani sistem artık yalnızca "event basan" değil, birden fazla bağımsız consumer ile gerçekten event-driven davranan bir yapıya yaklaşmış oldu.

## Kısa Sonuç
Faz 11 ile birlikte:

- gerçek bir audit consumer eklendi
- tüm temel todo eventleri dinlenir hale geldi
- audit kayıtları eventlerden üretildi
- tenant bilgisi korundu
- duplicate eventler ikinci audit kaydı üretmedi
- canlı Kafka + DB doğrulaması yapıldı

Bu faz, event-driven ekosistemde "bildirim"den sonra "operasyonel iz bırakma" ayağını da gerçek çalışan bir consumer olarak devreye alan faz oldu.
