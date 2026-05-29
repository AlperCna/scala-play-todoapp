# Kafka Phase 13

## Cok Kisa ve Basit Anlatim
Bu fazda yaptigimiz sey su:

- consumer'lar artik duplicate mesaji kalici olarak tanıyabiliyor
- bozuk mesaj gelince consumer dusmuyor
- islenemeyen mesajlar `todo.events.dlq.v1` topic'ine ayriliyor
- gecici hata olursa consumer hemen pes etmiyor, birkac kez tekrar deniyor

Yani Faz 13, consumer'lari "calisiyor" seviyesinden "biraz daha production gibi davraniyor" seviyesine tasidi.

## Faz 13'ten Once Neredeydik?
Faz 12 sonuna kadar producer tarafi oldukca iyi durumdaydi:

- domain event var
- outbox var
- worker var
- local Kafka var
- notification, audit, analytics consumer'lari var

Ama consumer guvenilirliginde hala bazi bosluklar vardi:

### 1. Notification consumer kalici degildi
`notification-consumer` duplicate kontrolunu `in-memory` yapiyordu.

Bu ne demek?

- process restart olursa hafiza sifirlanir
- ayni event tekrar gelirse ikinci kez islenebilirdi

Bu local demo icin idare eder, ama gercek hayatta yeterli degildir.

### 2. Bozuk mesaj icin net ayristirma yoktu
Malformed JSON ya da unsupported version durumunda consumer sadece ignore ediyordu.

Bu da su soruyu bos birakiyordu:

"Bu mesajlar sonra nerede gorulecek?"

Yani hata var ama operasyonel gorunurluk zayif.

### 3. Retry davranisi yoktu
Consumer processing sirasinda exception olursa bunun sistematik retry davranisi tanimli degildi.

### 4. DLQ kullanimi yoktu
DLQ topic planda vardi ama gercek kod akisina bagli degildi.

## Faz 13'te Ne Yaptik?
Bu fazda consumer guvenilirligini dort ana eksende gelistirdik:

1. persistent idempotency
2. malformed / unsupported mesajlar icin DLQ
3. retry policy
4. consumer dusmeden devam etme davranisi

## 1. Persistent Idempotency
### Notification consumer
Notification consumer'in en buyuk acigi buydu. Onu kapattik.

Eskiden:

- `InMemoryProcessedEventStore`

vardi.

Simdi:

- [JdbcProcessedEventStore.scala](/C:/Users/Alper/todo-play-app/consumers/todo-notification-consumer/src/main/scala/com/alper/todo/notificationconsumer/infrastructure/JdbcProcessedEventStore.scala:1)

var.

Bu store:

- `consumer_processed_events` tablosuna yazar
- `consumer_name + event_id` bazli duplicate kontrolu yapar

Yani artik notification consumer restart olsa bile daha once isledigi event'i unutmuyor.

### Audit ve Analytics consumer
Bu iki consumer zaten `consumer_processed_events` tablosunu kullaniyordu.

Yani Faz 13'te onlarin duplicate mantigi sifirdan yazilmadi; mevcut kalici yapilari korunup DLQ/retry mantigi ile tamamlandi.

## 2. DLQ Topic Kullanimi
Bu fazda gercek DLQ topic'i devreye alindi:

- `todo.events.dlq.v1`

### Local Kafka tarafinda ne degisti?
[docker-compose.yml](/C:/Users/Alper/todo-play-app/docker-compose.yml:1) icindeki `kafka-init` artik bu topic'i de olusturuyor.

Yani local ortamda:

- `todo.events.v1`
- `todo.events.dlq.v1`

birlikte geliyor.

### Consumer tarafinda ne oldu?
Her consumer icin bir Kafka DLQ publisher eklendi:

- [notification KafkaDeadLetterPublisher](/C:/Users/Alper/todo-play-app/consumers/todo-notification-consumer/src/main/scala/com/alper/todo/notificationconsumer/infrastructure/KafkaDeadLetterPublisher.scala:1)
- [audit KafkaDeadLetterPublisher](/C:/Users/Alper/todo-play-app/consumers/todo-audit-consumer/src/main/scala/com/alper/todo/auditconsumer/infrastructure/KafkaDeadLetterPublisher.scala:1)
- [analytics KafkaDeadLetterPublisher](/C:/Users/Alper/todo-play-app/consumers/todo-analytics-consumer/src/main/scala/com/alper/todo/analyticsconsumer/infrastructure/KafkaDeadLetterPublisher.scala:1)

Bu publisher'lar DLQ'ya sadece ham string atmak yerine yapili bir payload yaziyor:

- hangi consumer'dan geldi
- original topic neydi
- original key neydi
- hangi partition/offset'ten geldi
- neden DLQ'ya gitti
- ham payload neydi
- ne zaman ayrildi

Bu cok onemli. Cunku sadece "mesaj bozuktu" demek yerine sonradan forensic inceleme yapabiliyoruz.

## 3. Retry Policy
Her consumer app'ine basit bir retry politikasi eklendi.

Ayarlar:

- `maxRetries`
- `retryBackoffMillis`

Consumer processing sirasinda exception olursa:

1. hemen dusmuyor
2. belli sayida tekrar deniyor
3. yine basarisizsa DLQ'ya yollar

Bu sayede gecici sorunlarla kalici poison message'i birbirinden daha iyi ayirmaya basliyoruz.

Ilk versiyonda bu retry basit tutuldu:

- lineer tekrar
- `Thread.sleep` tabanli backoff

Bu production'da sonsuza kadar boyle kalmak zorunda degil; ama mantik olarak dogru ilk adim.

## 4. Consumer Dusmeden Devam Etme
Faz 13'te consumer dongulerine bir koruma daha koyduk.

Her record islenirken hata cikarsa:

- process tamamen crash olmasin
- hata loglansin
- consumer sonraki kayda gecebildigi kadar gecsin

Bu ozellikle malformed / unsupported / DLQ publish gibi sinirlarda onemli.

Buradaki hedef su:

"tek kotu mesaj tum consumer surecini oldurmesin"

## Hangi Consumer'larda Ne Degisti?
### Notification Consumer
Burasi Faz 13'te en cok degisen yer oldu.

Eklenenler:

- DB ayarlari
- `consumerName`
- `dlqTopic`
- `maxRetries`
- `retryBackoffMillis`
- persistent processed event store
- DLQ publisher
- retry loop

Yani `notification-consumer` ilk kez gercek anlamda production'a yaklasan consumer davranisi kazandi.

### Audit Consumer
Audit consumer zaten kalici duplicate korumasina sahipti.

Eklenenler:

- DLQ topic config
- retry config
- DLQ publisher
- retry loop
- unsupported event/version durumunda DLQ ayristirma

### Analytics Consumer
Analytics consumer da audit'e benzer sekilde:

- kalici duplicate korumasini korudu
- DLQ publisher aldi
- retry aldi
- malformed/unsupported mesajlari ayirabilir hale geldi

## Hangi Durumda Ne Oluyor?
Bu kismi cok net anlamak onemli.

### Duplicate event gelirse
- ikinci kez side-effect/projection/audit uretmez
- `DuplicateIgnored` olur
- offset commit edilir
- DLQ'ya gitmez

Neden DLQ'ya gitmiyor?

Cunku duplicate gelmesi sistem acisindan beklenen bir durumdur. Bu bir bozukluk degil, idempotency konusu.

### Malformed JSON gelirse
- consumer parse edemez
- bu kayit `MALFORMED_PAYLOAD` reason ile DLQ'ya gider
- consumer ayakta kalir
- offset commit edilir

### Unsupported version gelirse
- consumer bunu bilerek isleyemez
- `UNSUPPORTED_VERSION` reason ile DLQ'ya gider
- consumer ayakta kalir
- offset commit edilir

### Unsupported event type gelirse
Burada consumer bazli karar var.

- notification consumer icin `TodoUpdated` gibi eventler beklenen ama islenmeyen eventler olabilir
- audit/analytics icin ise unsupported event daha ciddi sinyal olabilir

Bu yuzden audit/analytics tarafinda unsupported event'i de DLQ'ya ayiriyoruz.

## Canli Olarak Neyi Test Ettik?
Bu fazi sadece unit test ile birakmadik. Canli local Kafka akisinda da dogruladik.

### 1. Duplicate test
Gercek app uzerinden yeni bir todo olusturduk:

- email: `phase13.1779885656@example.com`
- title: `Phase13 Notification 1779885656`

Bu event topic'e gitti. Sonra ayni `TodoCreated` eventini ikinci kez topic'e elle tekrar bastik.

Gozlem:

- `consumer_processed_events` tablosunda ilgili event icin sayi `1` kaldi
- consumer log'unda ikinci kayit `DuplicateIgnored` oldu

Bu suyu kanitladi:

- notification consumer artik kalici idempotency kullaniyor
- process restart olmasa bile duplicate mantigi DB tabanli

### 2. Malformed payload test
Topic'e bilerek bozuk bir mesaj bastik:

```json
{"broken":true}
```

Gozlem:

- consumer log'unda malformed payload warning'i goruldu
- kayit `MALFORMED_PAYLOAD` nedeni ile `todo.events.dlq.v1` topic'ine gitti
- consumer sureci ayakta kaldi

### 3. Unsupported version test
Topic'e eventVersion = `99` olan bir `TodoCreated` benzeri mesaj bastik.

Gozlem:

- consumer log'unda `UnsupportedVersionIgnored` goruldu
- kayit `UNSUPPORTED_VERSION` nedeni ile DLQ'ya gitti
- `consumer_processed_events` tablosuna yazilmadi
- consumer sureci ayakta kaldi

### 4. DLQ topic icerigi
`todo.events.dlq.v1` topic'ini okuyunca asagidaki tipte kayitlar gorduk:

- `reason = MALFORMED_PAYLOAD`
- `reason = UNSUPPORTED_VERSION`

Ve payload icinde:

- original topic
- original offset
- consumer name
- ham payload

gibi alanlar vardi.

Bu da "islenemeyen mesajlar izlenebilir sekilde ayrilmali" kabul kriterini karsiladi.

## Test Sonuclari
Bu fazda test edilen alanlar:

- root repo: `23/23` gecti
- notification consumer: `10/10` gecti
- audit consumer: `7/7` gecti
- analytics consumer test paketi gecti

Ek olarak canli lokal Kafka akisinda:

- duplicate
- malformed
- unsupported version

senaryolari dogrulandi.

## Neden Bu Faz Onemli?
Faz 10-11-12 bize consumer mantigini kazandirdi.
Faz 13 ise bu consumer'lari biraz daha gercek dunya dostu hale getirdi.

Kafka'da asil mesele sadece "mesaj okumak" degildir.
Asil mesele su sorulara cevap vermektir:

- mesaj tekrar gelirse ne yapacagim
- mesaj bozuksa ne yapacagim
- event version bekledigim gibi degilse ne yapacagim
- islerken exception olursa ne yapacagim
- hata olan mesaji sonra nerede gorecegim

Faz 13 tam olarak bu sorulara ilk ciddi cevabi verdi.

## Hala Neler Future Work?
Bu fazdan sonra bile halen ilerletilebilecek konular var:

- DLQ replay tooling
- consumer lag metrics
- retry icin daha gelismis exponential backoff
- ayrintili poison message dashboard
- notification consumer icin gercek email provider
- notification consumer idempotency icin daha zengin dispatch history
- schema registry / compatibility enforcement

Yani Faz 13 son nokta degil; ama consumer guvenilirligi icin cok onemli bir esik.

## Kisa Ozet
Bu fazda yaptigimiz sey:

- notification consumer'i in-memory duplicate mantigindan kalici duplicate mantigina tasidik
- uc consumer'a da retry ekledik
- bozuk ve islenemeyen mesajlari DLQ topic'ine ayirdik
- consumer'larin tek bir kotu mesaj yuzunden dusmesini engelledik

Kisacasi:

Faz 13 ile consumer tarafi "demo gibi calisiyor" seviyesinden "production mantigina yaklasiyor" seviyesine geldi.
