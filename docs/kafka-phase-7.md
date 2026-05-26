# Kafka Phase 7

## Bu Fazda Ne Problemi Cozmeye Calistik?
Faz 6 ile producer tarafi artik gercek broker ile calisabilecek hale geldi.
Ama event-driven mimari burada bitmez.

Asil soru su:

"Bu eventleri kim tuketecek ve nasil tuketecek?"

Consumer konusu producer'dan farkli bir dunyadir.
Cunku burada sadece mesaj okumazsin:
- duplicate mesajla yasarsin
- versiyon uyumsuzlugunu yonetirsin
- hangi eventlerin business olarak anlamli oldugunu secersin
- notification, audit, analytics gibi farkli ihtiyaclari ayirirsin

Faz 7'nin amaci tam da bu noktada ilk somut consumer iskeletini olusturmakti.

Bu fazda tum consumer ekosistemini bir anda yazmadik.
Onun yerine:
- ayri servis mantigini netlestirdik
- ilk consumer olarak `todo-notification-consumer` iskeleti kurduk
- desteklenen event kapsamini sinirladik
- idempotency ve version handling kararlarini koda tasidik

## Neden Consumer'i Ana Play App Icinde Yazmadik?
Bu kritik bir tasarim karari.

Eger consumer mantigini mevcut Play uygulamasina gomersek:
- monolith daha da sismanlar
- deploy sinirlari karisir
- actor/web request runtime'i ile Kafka polling runtime'i birbirine karisir
- ileride ayri consumer servis cikarirken ekstra tasima maliyeti olur

Bu yuzden bu fazda consumer'i repo icinde tuttuk ama fiziksel olarak ayirdik:

- `consumers/todo-notification-consumer`

Yani:
- ayni repo icinde
- ama ayri deploy edilebilir zihniyetle

Bu tam olarak planin istedigi seydi.

## Bu Fazda Ne Yaptik?

### 1. Ayri bir consumer klasoru olusturduk
Yeni klasor:
- `consumers/todo-notification-consumer`

Bu klasorun kendi:
- `build.sbt`
- `project/build.properties`
- `src/main`
- `test`
- `conf`
- `fixtures`

yapisi var.

Bu neden guzel?
Cunku artik bu consumer:
- ana Play app'ten bagimsiz dusunulebilir
- istersek sonra ayri pipeline ile build edilir
- servis siniri simdiden zihinde net olur

### 2. Notification consumer icin ayri event kontrati olusturduk
Producer tarafindaki envelope mantigina uyumlu bir consumer modeli yazdik:
- `TodoEventEnvelope`
- `TodoPayload`

Burada onemli olan sey su:
Consumer producer kodunu import etmek zorunda degil.
Kendi kontratini producer event sekline gore tanimliyor.

Bu cok onemli bir ders:
Consumerlar producer'in classpath'ine baglanmak zorunda degil.
Asil bagimlilik mesaj kontratidir.

### 3. JSON parse katmani yazdik
Yeni katman:
- `TodoEventJson`

Bu sinif producer'dan gelecek JSON event'i okuyup consumer modeline ceviriyor.

Bu neyi netlestirdi?
- event payload'inin consumer tarafinda nasil parse edilecegi artik belirsiz degil
- `UUID`, `Instant`, `LocalDateTime` alanlarinin nasil okunacagi kod seviyesinde belli

### 4. Ilk business kapsami bilincli olarak kucuk tuttuk
Notification consumer ilk asamada sadece su eventleri destekliyor:
- `TodoCreated`
- `TodoCompleted`

Bilerek disarida biraktik:
- `TodoUpdated`
- `TodoDeleted`

Neden?
Cunku ilk business ihtiyac notification.
Her guncellemede bildirim gondermek istemeyebiliriz.
Delete davranisi da ayrica business karari gerektirir.

Bu fazdaki dusunce su:
- "butun eventleri dinle" degil
- "gercekten gerekli eventleri dinle"

### 5. Dispatch mode mantigi ekledik
Yeni kavram:
- `NotificationDispatchMode`

Modlar:
- `disabled`
- `sandbox`
- `live`

Bu neden onemli?
Cunku consumer'i yazmakla onu gercek kullaniciya bildirim gonderecek sekilde calistirmak ayni sey degil.

Ozellikle ilk rollout'ta:
- sandbox mod cok degerlidir
- eventleri isler
- komut uretir
- ama gercek gonderim stratejisini kontrollu tutar

Bu, duplicate veya erken rollout riskini azaltir.

### 6. Idempotency davranisini iskelet seviyesinde kurduk
Yeni port:
- `ProcessedEventStore`

Ve processor mantiginda su karar var:
- eger `eventId` daha once islenmisse tekrar isleme

Bu consumer tarafinda cok onemlidir.
Cunku Kafka dunyasinda duplicate event gercek bir ihtimaldir.

Bu fazda kalici DB store yazmadik.
Ama idempotency kancasini dogru yere koyduk.

### 7. Notification sender portu tanimladik
Yeni port:
- `NotificationSender`

Bu ne ise yarar?
Consumer'in isi:
- event'i anlamak
- isleme karari vermek
- bir notification komutu uretmek

Gercek email/push/webhook gonderme isi ise ayri bir adapter olmalidir.

Bu ayirim sayesinde:
- test kolaylasir
- sandbox/live ayrimi rahatlar
- gercek entegrasyonlar sonradan eklenebilir

### 8. Asil is mantigini processor icinde topladik
Yeni sinif:
- `NotificationEventProcessor`

Bu sinif su kararlari veriyor:
- dispatch mode disabled mi
- event version destekleniyor mu
- event type notification icin destekleniyor mu
- event duplicate mi
- degilse notification komutu uret ve sender'a ilet

Yani Faz 7'nin asil beyni budur.

### 9. Ilk config ornegini ekledik
Yeni dosya:
- `consumers/todo-notification-consumer/conf/notification-consumer.conf.example`

Burada su bilgiler var:
- bootstrap servers
- topic
- group id
- dispatch mode
- supported event version

Bu sayede consumer deploy dusuncesi sadece kodda degil, config seviyesinde de baslamis oldu.

### 10. Fixture ve testlerle kontrati dogruladik
Yeni fixture:
- `fixtures/todo-created.json`

Bu dosya producer event formatinin consumer tarafinda ornek bir temsili oldu.

Boylece testler sadece soyut case class'lar ustunde degil, gercek JSON benzeri ornek uzerinden calisiyor.

## Teoride Burada Neyi Ogreniyoruz?

### Producer ve consumer ayni kodu paylasmak zorunda degildir
Bu cok onemli.
En guvenli bagimlilik:
- ortak class degil
- ortak mesaj kontrati

Bu yuzden consumer kendi parse modelini yazdi.
Bu gelecekte ayri repo olsa bile calisabilecek bir yaklasimdir.

### Neden her event her consumer icin anlamli degildir?
Bir topic'te cok event olabilir.
Ama her consumer sadece kendi business ihtiyacini secmelidir.

Notification consumer acisindan:
- `TodoCreated` anlamli
- `TodoCompleted` anlamli
- `TodoUpdated` belki gereksiz
- `TodoDeleted` business karari gerektirir

Bu secicilik iyi tasarimin parcasidir.

### Idempotency neden producer kadar consumer icin de onemlidir?
Producer ne kadar dikkatli olursa olsun:
- retry olabilir
- duplicate publish olabilir
- consumer restart olabilir

Bu yuzden consumer seviyesinde:
- `eventId` bazli tekrar isleme korumasi

olmasi gerekir.

Bu fazda bunu tam production storage ile degil, dogru soyutlama ile kurduk.

### Sandbox mode neden degerlidir?
Consumer'i hemen live modda acarsan:
- yanlis bildirim gonderebilirsin
- duplicate davranis uretirsin
- rollout sirasinda gurultu olusturabilirsin

Sandbox mode producer tarafindaki local rollout mantigina benzer sekilde kontrollu gecis sunar.

## Testte Neyi Kanitladik?

### 1. JSON event parse testi
Yeni test:
- `TodoEventJsonSpec`

Bu test producer formatindaki bir fixture'i okuyup consumer modeline parse edebildigimizi kanitliyor.

Bu neyi ispatlar?
- event contract producer ve consumer arasinda uyumlu
- consumer JSON parsing katmani calisiyor

### 2. Processor mutlu yol testi
Yeni test:
- `NotificationEventProcessorSpec`

Ilk senaryo:
- `TodoCreated`
- sandbox mode
- version uyumlu
- duplicate degil

Beklenen:
- event islenir
- notification komutu uretilir
- event processed olarak isaretlenir

### 3. Duplicate ignore testi
Ayni spec icinde:
- event id daha once store'da varsa
- consumer tekrar gonderim yapmaz

Bu idempotency kancasinin dogru yerde oldugunu gosterir.

### 4. Unsupported version testi
Eger `eventVersion` beklenenden farkliysa:
- event ignore edilir
- sender cagrilmaz

Bu da versiyon uyumsuzlugu karsisinda temkinli davrandigimizi gosterir.

### 5. Unsupported event type testi
`TodoDeleted` gibi su an kapsam disi bir event gelirse:
- consumer bunu ignore eder

Bu da "her eventi her zaman isle" yerine secici business davranisini dogrular.

### 6. Disabled mode testi
Consumer disabled moddaysa:
- event gorse bile isleme girmez

Bu rollout ve operasyon mantigi icin degerlidir.

## Bu Fazdan Sonra Sistem Hangi Seviyeye Geldi?
Bu noktada artik:
- producer tarafi calisabilir durumda
- local broker akisi hazir
- ve ilk consumer iskeleti repo icinde fiziksel olarak ayrilmis durumda

Yani mimari tek yonlu degil artik.
Ilk kez producer'in karsisinda gercek bir consumer taslagi var.

## Hala Ne Eksik?
Faz 7 bittiginde bile hala future work var:
- gercek Kafka client ile polling loop
- offset commit stratejisi
- kalici processed event store
- gercek email/push/webhook adapter'i
- audit consumer
- analytics consumer

Yani Faz 7 tam consumer sistemi degil.
Ama consumer mimarisini artik kagit ustunden alip kod seviyesine indirdik.
