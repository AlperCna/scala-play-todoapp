# Kafka Phase 12

## Cok Kisa ve Basit Anlatim
Bu fazi tek cumlede anlatirsak:

"Todo eventlerini okuyup analytics icin ayri tablo dolduran bir servis yazdik."

Biraz daha acarsak:

- uygulama kendi `todos` tablosunda isini yapiyor
- Kafka'ya `TodoCreated`, `TodoUpdated`, `TodoCompleted`, `TodoDeleted` eventleri gidiyor
- analytics consumer bu eventleri dinliyor
- ve kendi analytics tablolari icinde "bu tenant'ta kac todo var, kac tanesi tamamlanmis, bugun kac tane create oldu" gibi bilgileri biriktiriyor

Yani analytics artik gidip `todos` tablosunu dogrudan didiklemek zorunda degil.

## Faz 12'in Amaci Neydi?
Bu fazin hedefi, Kafka'daki todo eventlerinden analytics verisi uretmekti. Buradaki kritik fikir su:

- operational uygulama kendi `todos` tablosuyla calisir
- Kafka producer bu is akisini event olarak disari cikarir
- analytics consumer ise bu eventleri okuyup kendi projection tablosunu ve kendi metrik tablolarini uretir

Yani bu fazda yaptigimiz sey sadece "bir event daha tuksun" degil. Asil yaptigimiz sey:

`business state` ile `analytics state` arasina event-driven bir kopyalama mekanizmasi koymak.

Boylece analytics verisi artik dogrudan `todos` tablosunu sorgulayarak uretilmek zorunda degil. Bunun yerine analytics tarafi sadece eventlerden beslenebilir.

Bu, Kafka dusuncesinde cok onemli bir adimdir. Cunku gercek sistemlerde analytics, reporting, dashboard ve projection okumalari cogu zaman operational tablodan dogrudan yapilmamaya calisilir.

## Faz 12'den Once Sistem Neredeydi?
Faz 11 sonuna kadar elimizde sunlar vardi:

- uygulama todo eventlerini uretiyordu
- bu eventler outbox ile guvenli sekilde saklaniyordu
- worker bu eventleri Kafka'ya publish ediyordu
- notification consumer eventleri okuyabiliyordu
- audit consumer eventleri okuyup `audit_logs` tablosuna event-driven kayit uretebiliyordu

Ama analytics tarafi eksikti.

Bu ne demekti?

- bir tenant icin kac todo olusturulmus
- kac tanesi tamamlanmis
- completion rate nedir
- bugun kac create/completed olayi olmus

gibi sorulari eventlerden ureten ayri bir katman yoktu.

Yani sistemde veri vardi ama analytics projection yoktu.

## Bu Fazda Ne Yaptik?
Bu fazda repo icine ayri bir consumer modulu ekledik:

- [todo-analytics-consumer](/C:/Users/Alper/todo-play-app/consumers/todo-analytics-consumer/README.md)

Bu consumer:

- `TodoCreated`
- `TodoUpdated`
- `TodoCompleted`
- `TodoDeleted`

eventlerini Kafka'dan okuyor ve uc ayri analytics tablosu uretip guncelliyor:

1. `tenant_todo_analytics_projection`
2. `tenant_todo_analytics_summary`
3. `tenant_todo_daily_metrics`

## Neden Uc Ayrı Tablo Kullandik?
Burada bilincli olarak tek tabloyla her seyi cozmedik. Cunku analytics sorulari farkli turdedir.

### 1. `tenant_todo_analytics_projection`
Bu tablo todo'nun analytics gozuyle son halini tutar.

Burada sunlar var:

- todo hangi tenant'a ait
- title nedir
- description nedir
- due date nedir
- todo su an completed mi
- todo su an deleted mi
- en son hangi event ile degisti

Bu tabloyu su mantikla dusun:

"Kafka eventlerinden turetilmis bir read model"

Yani uygulamanin `todos` tablosunun analytics kopyasi gibi, ama dogrudan operational tablodan degil, eventlerden besleniyor.

### 2. `tenant_todo_analytics_summary`
Bu tablo tenant seviyesinde hizli ozet tutar.

Burada sunlari hesapliyoruz:

- toplam izlenen todo sayisi
- aktif todo sayisi
- completed todo sayisi
- open todo sayisi
- deleted todo sayisi
- toplam created event sayisi
- toplam updated event sayisi
- toplam completed event sayisi
- toplam deleted event sayisi
- completion rate

Boylece "bir tenant'in genel durumu ne?" sorusu icin projection tablosunun tamami her seferinde elle okunmak zorunda kalmaz.

### 3. `tenant_todo_daily_metrics`
Bu tablo gunluk metrik icindir.

Her tenant ve her gun icin sunlari tutuyoruz:

- created_count
- updated_count
- completed_count
- deleted_count

Bu, dashboard veya trend gibi raporlar icin cok kullanislidir.

Ornegin:

- bugun kac todo olustu
- dun kac todo tamamlandi
- bir tenant bu hafta ne kadar aktifti

gibi sorular buradan cevaplanabilir.

## Evolution Tarafinda Ne Eklendi?
Yeni evolution dosyasi:

- [6.sql](/C:/Users/Alper/todo-play-app/conf/evolutions/default/6.sql)

Bu dosya:

- analytics projection tablosunu
- summary tablosunu
- daily metrics tablosunu

olusturuyor.

Burada bilerek `todos` tablosuna foreign key ile baglanmadik. Cunku amac analytics tarafinin eventlerden beslenen bir projection olmasi. Yani tasarim olarak analytics katmani operational tablolarla gereksiz yere cok siki baglanmiyor.

Bu tam anlamiyla "ayri database" demek degil, ama "ayri veri modeli" demek.

## Consumer Icindeki Ana Parcalar
### `AnalyticsConsumerApp`
- [AnalyticsConsumerApp.scala](/C:/Users/Alper/todo-play-app/consumers/todo-analytics-consumer/src/main/scala/com/alper/todo/analyticsconsumer/AnalyticsConsumerApp.scala:1)

Bu sinif gercek Kafka polling dongusunu calistirir.

Gorevi:

- Kafka consumer'i baslatmak
- `todo.events.v1` topic'ine subscribe olmak
- gelen eventleri tek tek handler'a vermek
- isleme bittikten sonra offset commit etmek

Buradaki kritik nokta:

`auto-commit` kapali.

Yani offset kendiliginden ilerlemiyor. Consumer eventi isledikten sonra commit ediyor. Bu, "mesaj goruldu" ile "mesaj gercekten uygulandi" arasini ayirmak icin onemli.

### `AnalyticsCommandFactory`
- [AnalyticsCommandFactory.scala](/C:/Users/Alper/todo-play-app/consumers/todo-analytics-consumer/src/main/scala/com/alper/todo/analyticsconsumer/service/AnalyticsCommandFactory.scala:1)

Bu sinif Kafka event'ini projection yazicisinin kullanabilecegi daha sade bir komuta cevirir.

Yani envelope icindeki JSON payload buradan okunur ve daha anlamli alanlara ayrilir:

- todoId
- title
- description
- isCompleted
- dueDate
- createdAt
- updatedAt
- deletedAt
- occurredAt

Burada kritik fikir su:

Kafka event'i ile projection writer'i dogrudan birbirine yapistirmiyoruz.

Araya command factory koyunca:

- parsing tek yerde toplanir
- business mapping tek yerde toplanir
- test yazmak kolaylasir

### `AnalyticsEventProcessor`
- [AnalyticsEventProcessor.scala](/C:/Users/Alper/todo-play-app/consumers/todo-analytics-consumer/src/main/scala/com/alper/todo/analyticsconsumer/service/AnalyticsEventProcessor.scala:1)

Bu sinif karar verir:

- event version destekleniyor mu
- event type destekleniyor mu
- destekleniyorsa projection writer'a ver

Yani consumer'in business kapisi gibi dusunebilirsin.

### `JdbcAnalyticsProjectionWriter`
- [JdbcAnalyticsProjectionWriter.scala](/C:/Users/Alper/todo-play-app/consumers/todo-analytics-consumer/src/main/scala/com/alper/todo/analyticsconsumer/infrastructure/JdbcAnalyticsProjectionWriter.scala:1)

Faz 12'nin asil agirligi burada.

Bu sinif tek transaction icinde sunlari yapar:

1. event daha once islenmis mi bakar
2. duplicate ise projection'i tekrar bozmaz
3. projection tablosunu gunceller
4. daily metrics tablosunu gunceller
5. summary tablosunu yeniden hesaplar
6. `consumer_processed_events` tablosuna bu event'in islendigi kaydini yazar

Buradaki en onemli karar su:

`projection update + metrics update + processed event insert`

ayni transaction icinde olur.

Bu niye onemli?

Eger projection guncellenip duplicate kaydi yazilmazsa, ayni event tekrar geldiginde ikinci kez sayim bozulabilir.
Eger duplicate kaydi yazilip projection guncellenmezse, event "islenmis" gorunur ama analytics eksik kalir.

Bu yuzden bunlar tek transaction icinde olmali.

## Duplicate Event Problemini Nasil Yonettik?
Kafka tarafinda duplicate ihtimali her zaman vardir. Consumer yeniden baslayabilir, ayni event tekrar okunabilir, operator event'i elle yeniden topic'e yazabilir.

Biz burada su kurali koyduk:

- her consumer kendi adiyla `consumer_processed_events` tablosunda event'i isaretler
- `consumer_name + event_id` birlikte tekildir

Bu sayede `todo-audit-consumer` ile `todo-analytics-consumer` birbirini engellemez. Ayni event iki farkli consumer tarafindan ayri ayri islenebilir. Ama ayni consumer ayni event'i ikinci kez projection'a uygulamaz.

## Completion Rate Nasil Hesaplaniyor?
Bu raporda acik acik yazmak onemli:

completion rate burada su formulle hesaplandi:

`completed_todos / active_todos`

Yani silinmemis todo'lar icindeki tamamlanmis oran.

Bu tasarim bilincli:

- `deleted` olan kayitlari aktif backlog hesabina katmiyoruz
- aktif is yuku icindeki tamamlanmis oranini gormek istiyoruz

Ileride istenirse baska completion rate tanimlari da eklenebilir:

- `completed_events / created_events`
- belirli tarih araligi icin completion rate
- tenant bazli weekly completion rate

Ama ilk versiyon icin aktif backlog bazli oran yeterli ve anlasilir.

## Canliya Yakın Olarak Ne Test Edilecek?
Bu fazin canli dogrulama mantigi su olmali:

1. Kafka broker acik olacak
2. App `localhost:9000` ustunde Kafka acik modda calisacak
3. Analytics consumer ayaga kalkacak
4. Yeni bir todo create/update/complete/delete akisi uretilecek
5. Kafka eventleri analytics consumer tarafindan okunacak
6. `tenant_todo_analytics_projection` tablosunda ilgili todo'nun son hali olusacak
7. `tenant_todo_daily_metrics` tablosunda o gunun sayilari artacak
8. `tenant_todo_analytics_summary` tablosunda tenant bazli sayilar guncellenecek

Buradaki en onemli teori su:

Analytics consumer `todos` tablosuna bakmadan da bu veriyi uretebiliyor olmali.

Yani projection eventlerden doguyor.

## Canli Dogrulamada Ne Yaptik?
Bu fazi sadece unit test ile birakmadik. Gercek local Kafka ve gercek Play app ustunde de denedik.

Canli testte yaptigimiz seyler:

1. Kafka broker'in acik oldugunu dogruladik
2. Play app'i Kafka acik modda `localhost:9000` uzerinde kaldirdik
3. analytics consumer'i ayaga kaldirdik
4. Yeni bir kullanici olusturduk
5. Gercek bir todo icin su akisi uyguladik:
   - create
   - update
   - complete
   - delete
6. Sonra SQL uzerinden analytics tablolarini okuduk

Kullanilan canli ornek:

- kullanici: `analytics.full.1779883146@example.com`
- create title: `Analytics Create 1779883146`
- update sonrasi title: `Analytics Updated 1779883146`
- todo id: `08128FBC-BF8C-47CE-9E57-225A58740259`
- tenant id: `93416698-B138-418B-9CAA-961676234EF4`

### Create Sonrasi Gordugumuz Durum
Create event'i islendikten sonra projection tablosunda su kaydi gorduk:

- title = `Analytics Create 1779883146`
- `is_completed = 0`
- `is_deleted = 0`
- `last_event_type = TodoCreated`

Bu bize suyu kanitladi:

- create event projection'a ulasmis
- analytics consumer todo'nun ilk state'ini kendi tablosuna yazmis

### Update + Complete + Delete Sonrasi Gordugumuz Durum
Kalan uc event de islendikten sonra projection satiri su hale geldi:

- title = `Analytics Updated 1779883146`
- `is_completed = 1`
- `is_deleted = 1`
- `last_event_type = TodoDeleted`
- `completed_at = 2026-05-27T15:00:04`
- `deleted_at = 2026-05-27T15:00:04`

Bu neyi kanitladi?

- update event title'i projection'a tasidi
- complete event completed state'ini analytics tarafina yansitti
- delete event son state'i delete olarak projection'a kapatti

Yani projection tablo gercekten eventlerin son durumunu tutuyor.

### Summary Tablosunda Gordugumuz Fark
Tenant summary tablosunda create sonrasi ve tum akisin sonundaki degerler arasinda su farki gorduk:

Create sonrasi:

- `created_events = 6`
- `updated_events = 2`
- `completed_events = 2`
- `deleted_events = 2`
- `active_todos = 4`
- `open_todos = 4`
- `deleted_todos = 2`

Tum akis bittikten sonra:

- `created_events = 6`
- `updated_events = 3`
- `completed_events = 3`
- `deleted_events = 3`
- `active_todos = 3`
- `open_todos = 3`
- `deleted_todos = 3`

Buradaki en guzel nokta su:

- create olayi zaten baseline'da gorunuyordu
- sonra update/completed/deleted sayilari tam birer birer artti
- aktif/open todo sayisi bir azalirken deleted sayisi bir artti

Yani summary tablo da eventlerden dogru sekilde yeniden hesaplandi.

### Daily Metrics Tablosunda Gordugumuz Fark
Ayni tenant ve ayni gun icin daily metrics satirinda su farki gorduk:

Create sonrasi:

- `created_count = 2`
- `updated_count = 1`
- `completed_count = 1`
- `deleted_count = 1`

Tum akis bittikten sonra:

- `created_count = 2`
- `updated_count = 2`
- `completed_count = 2`
- `deleted_count = 2`

Bu tablo da bize sunu kanitladi:

- gunluk metrikler event bazli artiyor
- analytics sadece son state tutmuyor
- ayni zamanda event hacmini gun gun biriktiriyor

### Consumer Log'unda Ne Gorduk?
Analytics consumer log'unda su tip satirlar gorduk:

- `result=Processed(Processed)`
- daha once elle tekrar basilan bazi tarihi eventlerde `DuplicateIgnored`

Bu da duplicate korumasinin analytics tarafinda da devrede oldugunu gosterdi.

## Acceptance Criteria Bu Fazda Nasil Karsilandi?
Bu fazin kabul kriteri suydu:

`analytics verisi app DB'den bagimsiz eventlerden uretilebilmeli`

Bunu nasil karsiladik?

- analytics consumer Kafka'dan event okuyor
- kendi projection tablosunu eventten dolduruyor
- kendi summary tablosunu eventten hesapliyor
- kendi daily metrics tablosunu eventten biriktiriyor

Yani analytics okumasi icin `todos` tablosuna gidip "hadi bana rapor cikar" demiyoruz.

Elbette veri ayni fiziksel database icinde tutuluyor olabilir. Ama mantiksal olarak artik farkli bir veri modeli var. Analytics tarafinin gercegi eventlerden tureyen projection tablosu.

## Bu Fazda Hangi Testleri Yazdik?
### Settings loader testi
- config dogru okunuyor mu

### Event processor testi
- unsupported version ignore ediliyor mu
- unsupported event ignore ediliyor mu
- supported event writer'a gidiyor mu

### Kafka record handler testi
- bozuk JSON ignore ediliyor mu
- gecerli payload icin processor sonucu donuyor mu

Bu testler projection SQL'inin her ayrintisini tek basina kanitlamaz; ama consumer aklinin iskeletini kilitler.

Ek olarak canli testte su seyleri de dogruladik:

- evolution `6.sql` uygulandi
- `play_evolutions` tablosunda `id = 6` kaydi olustu
- projection, summary ve daily metrics tablolarina gercek veri dustu
- app `localhost:9000` uzerinde gercek request aldi
- Kafka topic'teki eventler analytics consumer tarafindan islenebildi

## Bu Fazdan Sonra Sistem Ne Kazandi?
Faz 12'den sonra elimizde artik uc ayri event-driven consumer yonu var:

- notification
- audit
- analytics

Bu cok onemli bir esik.

Cunku artik Kafka entegrasyonu sadece "event basiyoruz" seviyesinde degil. Gercekten:

- yan etki
- audit
- analytics

gibi farkli use-case'lere event dagitimi yapan bir sisteme donusuyor.

Bu da producer-first tasarimin niye anlamli oldugunu somutlastiriyor.

## Hala Neler Eksik?
Bu fazin sonunda bile su seyler future work kalir:

- analytics consumer icin ayri admin/read endpoint'leri
- daha gelismis chart/projection ekranlari
- persistent lag/metrics gozlemi
- DLQ veya poison message handling
- daha gelismis trend projection'lari
- event schema evrimi buyudukce analytics migration stratejisi

Yani Faz 12 analytics temelini kurar; tam raporlama urununu degil.

## Kisa Ozet
Bu fazda yaptigimiz sey su:

- Kafka'daki todo eventlerini okuyan ayri bir analytics consumer yazdik
- eventlerden projection tablosu urettik
- tenant bazli summary urettik
- gunluk metrik tablosu urettik
- duplicate eventlerin sayimlari bozmamasini sagladik
- analytics verisini operational todo tablosundan bagimsiz dusunmeye basladik

Kisaca:

Faz 12, "event var" seviyesinden "eventten analytics okunabilir" seviyesine gecis fazidir.
