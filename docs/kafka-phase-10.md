# Kafka Phase 10 - Notification Consumer

## Fazın Amacı
Bu fazın amacı, producer tarafında kurduğumuz event akışının gerçekten başka bir süreç tarafından tüketilebildiğini göstermekti. Önceki fazlarda şunları çözmüştük:

- Play uygulaması domain event üretiyor
- bu eventler outbox tablosuna güvenli şekilde yazılıyor
- worker bu eventleri Kafka topic'ine publish ediyor
- Kafka UI üzerinden eventlerin topic'e düştüğünü görebiliyoruz

Ama bütün bunlara rağmen hâlâ eksik olan çok önemli bir halka vardı:

> "Bu eventleri başka bir servis gerçekten okuyabiliyor mu?"

Kafka entegrasyonunun asıl gücü burada başlar. Çünkü Kafka'nın değeri sadece mesaj basmak değil, aynı business olayını farklı servislerin bağımsız şekilde kullanabilmesidir. Bu yüzden Faz 10, producer tarafını genişletmekten çok, consumer tarafında ilk gerçek çalışan servisi ayağa kaldırmaya odaklandı.

## Bu Fazdan Önce Sistem Neredeydi?
Faz 9 sonuna kadar sistemin durumu şuydu:

- Todo app event üretiyordu
- eventler Kafka'ya gidiyordu
- topic içinde `TodoCreated`, `TodoUpdated`, `TodoCompleted`, `TodoDeleted` mesajları oluşuyordu
- outbox failure, retry ve replay gibi producer tarafı operasyonlar çalışıyordu

Ama consumer tarafı için elimizde yalnızca bir iskelet vardı. Yani:

- event contract parse edilebiliyordu
- bazı business kuralları test edilmişti
- ama gerçek Kafka consumer loop yoktu
- offset commit yoktu
- topic'ten okuma yoktu
- ayrı bir servis gibi çalışan entrypoint yoktu

Bu nedenle Faz 10 ile birlikte consumer tarafını "tasarım fikri" olmaktan çıkarıp "çalışabilen süreç" haline getirdik.

## Bu Fazda Neyi Hedefledik?
Bu fazla ulaşmak istediğimiz hedefler şunlardı:

- `todo.events.v1` topic'ini gerçekten dinleyen bir süreç oluşturmak
- yalnızca anlamlı bildirim eventlerini işlemek
- ilk aşamada `TodoCreated` ve `TodoCompleted` ile başlamak
- unsupported eventleri sistematik biçimde ignore etmek
- malformed payload gelirse consumer'ı düşürmemek
- duplicate eventleri ikinci kez etki üretmeden pas geçebilmek
- başarılı işlenen mesajlarda offset commit etmek
- gerçek email gönderimi yerine önce güvenli bir `sandbox/logging` davranışıyla ilerlemek

Bu hedef özellikle önemliydi, çünkü consumer tarafında en sık yapılan hata şudur:

- daha ilk adımda gerçek email/SMS/push sistemine bağlanmak
- sonra parse, idempotency, offset, unsupported event, malformed payload gibi temel şeyleri ihmal etmek

Bu projede o hataya düşmedik. Önce consumer davranışını güvenli ve gözlemlenebilir kurduk.

## Neden Ayrı Consumer Modülü?
Notification consumer ana Play uygulamasının içine gömülmedi. Bunun yerine şu klasörde fiziksel olarak ayrı tutuldu:

- [todo-notification-consumer](C:/Users/Alper/todo-play-app/consumers/todo-notification-consumer/README.md)

Bunu özellikle böyle yaptık çünkü uzun vadeli hedef şu:

- monolith Play app event üreticisi olsun
- consumer servisleri ayrı süreçler olsun
- deploy, ölçekleme, hata yönetimi ve yaşam döngüsü birbirinden ayrışabilsin

Bu ayrım teoride de doğru bir event-driven tasarım davranışıdır. Eğer consumer mantığını tekrar monolith içine gömseydik, Kafka'yı eklemiş olurduk ama bağımsız servis kazanımını kaçırırdık.

## Bu Fazda Eklenen Ana Parçalar

### 1. Gerçek Consumer Entry Point
En önemli yeni parça:

- [NotificationConsumerApp.scala](/C:/Users/Alper/todo-play-app/consumers/todo-notification-consumer/src/main/scala/com/alper/todo/notificationconsumer/NotificationConsumerApp.scala:1)

Bu dosya artık consumer modülünü gerçekten çalıştıran ana giriş noktası. Bu sınıf:

- Kafka consumer oluşturur
- topic'e subscribe olur
- belirli aralıklarla poll yapar
- gelen kayıtları handler katmanına verir
- sonuç başarılıysa offset commit eder
- kapanışta consumer'ı düzgün kapatır

Bu çok önemli bir eşikti. Çünkü bu noktadan sonra consumer yalnızca test sınıflarından ibaret değil; bağımsız bir runtime süreci.

### 2. Config Loader
Yeni config loader:

- [NotificationConsumerSettingsLoader.scala](/C:/Users/Alper/todo-play-app/consumers/todo-notification-consumer/src/main/scala/com/alper/todo/notificationconsumer/config/NotificationConsumerSettingsLoader.scala:1)

Bu parça consumer ayarlarını config'ten yüklüyor. Böylece:

- topic sabit koda gömülmüyor
- group id dışarıdan yönetilebiliyor
- dispatch mode değiştirilebiliyor
- supported event version ayarlanabiliyor

Şu an consumer config yüzeyi şunları içeriyor:

- `bootstrapServers`
- `topic`
- `groupId`
- `dispatchMode`
- `supportedEventVersion`

### 3. Record Handler
Yeni handler:

- [NotificationKafkaRecordHandler.scala](/C:/Users/Alper/todo-play-app/consumers/todo-notification-consumer/src/main/scala/com/alper/todo/notificationconsumer/service/NotificationKafkaRecordHandler.scala:1)

Bu sınıf iki sorumluluğu ayırıyor:

- raw Kafka message -> JSON parse
- parse başarılıysa business processor'a verme

Bu ayrım neden önemli?

Çünkü consumer tarafında her hata business hatası değildir. Bazı hatalar:

- bozuk JSON
- eksik alan
- beklenmeyen şema
- yanlış payload

gibi "payload seviyesinde" hatalardır. Bunları business katmanına kadar taşımak yerine handler seviyesinde yakalamak çok daha temiz bir tasarım verir.

Bu handler sayesinde malformed payload'lar:

- consumer sürecini patlatmıyor
- ignore ediliyor
- log üzerinden görünür kalıyor

### 4. In-Memory Idempotency Store
Yeni sınıf:

- [InMemoryProcessedEventStore.scala](/C:/Users/Alper/todo-play-app/consumers/todo-notification-consumer/src/main/scala/com/alper/todo/notificationconsumer/infrastructure/InMemoryProcessedEventStore.scala:1)

Bu sınıf ilk idempotency zemini. Teoride `eventId` tekrar gelirse consumer'ın ikinci kez etki üretmemesi gerekir. Bu fazda bunu kalıcı veritabanı ile değil, basit in-memory yapı ile çözdük.

Bu çözümün değeri şu:

- duplicate handling fikri koda indirildi
- processor gerçekten `eventId` bazlı karar veriyor
- sonraki fazda bunu DB tablosuna veya Redis benzeri store'a taşımak kolay olacak

Ama sınırlamayı açıkça söylemek gerekir:

- uygulama yeniden başlarsa bu hafıza sıfırlanır
- bu yüzden production-grade idempotency değildir
- bu faz için yeterli ama final çözüm değildir

### 5. Logging/Sandbox Notification Sender
Yeni sınıf:

- [LoggingNotificationSender.scala](/C:/Users/Alper/todo-play-app/consumers/todo-notification-consumer/src/main/scala/com/alper/todo/notificationconsumer/infrastructure/LoggingNotificationSender.scala:1)

Bu sender gerçek email göndermiyor. Bunun yerine:

- event işlendiğinde log yazar
- `dispatchMode` bilgisini korur
- hangi event'in işlendiğini görünür hale getirir

Bu özellikle iyi bir geliştirme yaklaşımıdır. Çünkü consumer tarafında önce şu soruları çözmek isteriz:

- event geliyor mu
- parse oluyor mu
- doğru event type seçiliyor mu
- duplicate logic çalışıyor mu
- commit ediliyor mu

Bunlar çözülmeden gerçek provider entegrasyonuna gitmek gereksiz risktir.

### 6. Config Dosyası ve Start Script
Consumer için eklenen çalışma yüzeyi:

- [application.conf](/C:/Users/Alper/todo-play-app/consumers/todo-notification-consumer/conf/application.conf:1)
- [start-notification-consumer.ps1](/C:/Users/Alper/todo-play-app/scripts/start-notification-consumer.ps1:1)

Böylece consumer modülünü gerçekten başlatmak mümkün hale geldi.

## Bu Fazda Consumer Hangi Eventleri İşliyor?
Şu an desteklenen eventler:

- `TodoCreated`
- `TodoCompleted`

Şu an bilinçli olarak ignore edilen eventler:

- `TodoUpdated`
- `TodoDeleted`

Bu bilinçli bir business kararı. Çünkü ilk hedef "notification consumer" oluşturmak. Her event mutlaka bildirim doğurmak zorunda değil.

Örneğin:

- todo oluşturulduğunda bildirim mantıklı olabilir
- todo tamamlandığında bildirim mantıklı olabilir
- ama her update için bildirim spam yaratabilir
- delete için de product kararı ayrıca gerekebilir

Yani bu ignore davranışı eksiklik değil, ilk kapsamı kontrollü tutma tercihidir.

## Offset Commit Davranışı
Bu fazda çok önemli bir karar daha kodlandı:

> Offset commit yalnızca mesaj handle edildikten sonra yapılır.

Bu ne demek?

Kafka consumer bir mesajı görür görmez offset commit etseydi, işlem ortada hata verirse mesaj kaybolmuş gibi davranılırdı. Bu yüzden burada şu akış kullanıldı:

1. Kafka record poll edilir
2. handler parse eder
3. processor business kararını verir
4. sender çağrılır
5. duplicate değilse processed store güncellenir
6. ancak bundan sonra offset commit edilir

Bu sayede en azından ilk consumer sürümünde güvenli bir "başarılı işlemden sonra commit" mantığı kuruldu.

## Canlı Olarak Ne Doğrulandı?
Bu faz yalnızca unit test ile bırakılmadı. Gerçek smoke test de yaptık.

Doğrulananlar:

- consumer Kafka topic'ine bağlandı
- mevcut `todo.events.v1` içindeki eventleri okumaya başladı
- `TodoCreated` eventleri için sandbox notification log'u üretti
- `TodoCompleted` eventi için sandbox notification log'u üretti
- `TodoUpdated` ve `TodoDeleted` eventlerini `UnsupportedEventIgnored` olarak geçti
- process sırasında consumer düşmedi

Bu canlı test önemliydi çünkü:

- config loader çalışıyor mu
- app sınıfı ayağa kalkıyor mu
- Kafka client gerçekten bağlanıyor mu
- offset ve poll akışı dönüyor mu

gibi sorular ancak gerçek çalıştırma ile cevaplanabilir.

## Testler
Bu fazla birlikte consumer modülü test yüzeyi genişledi.

Yeni eklenen testler:

- [NotificationConsumerSettingsLoaderSpec.scala](/C:/Users/Alper/todo-play-app/consumers/todo-notification-consumer/test/com/alper/todo/notificationconsumer/config/NotificationConsumerSettingsLoaderSpec.scala:1)
- [NotificationKafkaRecordHandlerSpec.scala](/C:/Users/Alper/todo-play-app/consumers/todo-notification-consumer/test/com/alper/todo/notificationconsumer/service/NotificationKafkaRecordHandlerSpec.scala:1)

Mevcut testlerle birlikte doğrulananlar:

- valid producer payload parse ediliyor mu
- malformed payload güvenli şekilde ignore ediliyor mu
- supported event işleniyor mu
- duplicate event ignore ediliyor mu
- unsupported version ignore ediliyor mu
- unsupported event ignore ediliyor mu
- disabled mode tüm eventleri pas geçiyor mu
- config loader doğru ayarları okuyor mu

Son test sonucu:

- consumer modülü `10/10` test geçti

## Bu Fazda Öğrendiğimiz Teorik Noktalar
Bu faz yalnızca kod değil, Kafka mantığı açısından da önemli birkaç ilkeyi görünür hale getirdi:

### Producer tamam olmak consumer tamam olmak değildir
Bir topic'e mesaj düşmesi, sistemin tamamlandığı anlamına gelmez. Gerçek değer, bağımsız consumer'ların o veriyi kullanmasıyla oluşur.

### Consumer tarafında payload güvenliği şarttır
Producer doğru çalışsa bile consumer bozuk veri görebilir. Bu yüzden parse ve business processing ayrımı gereklidir.

### İdempotency consumer'ın kalbidir
Aynı event ikinci kez gelirse iki kez bildirim atmak istemeyiz. Bu yüzden `eventId` bazlı işlenmiş event kaydı çok önemli bir design point.

### Offset commit rastgele yapılmaz
Commit zamanlaması delivery semantics açısından kritik karardır. Bu fazda erken commit yerine başarılı işlem sonrası commit tercih edildi.

### Her event mutlaka işlenmek zorunda değildir
Consumer'ın unsupported eventleri bilinçli şekilde ignore etmesi de sağlıklı bir davranıştır. Bu, consumer'ı kapsamlı ama kontrolsüz yapmak yerine, odaklı ve güvenli yapar.

## Bilerek Yapmadıklarımız
Bu fazda özellikle yapmadığımız şeyler de önemli:

- gerçek email provider entegrasyonu
- persistent processed event store
- retry/backoff consumer tarafı
- dead letter queue
- audit consumer
- analytics consumer
- notification command'larının veritabanına yazılması
- health endpoint / metrics endpoint

Bunlar yapılmadı çünkü Faz 10'un hedefi "çalışan ilk gerçek consumer" çıkarmaktı. Hepsini bir anda yaparsak hem fazın amacı bulanıklaşır, hem de sorun olduğunda hangi parçanın problemli olduğunu anlamak zorlaşır.

## Bu Fazdan Sonra Sistemin Yeni Durumu
Artık sistemin genel resmi şöyle:

1. User todo oluşturur/günceller/tamamlar/siler
2. Play app domain event üretir
3. Event outbox tablosuna yazılır
4. Worker bunu Kafka topic'ine publish eder
5. Notification consumer topic'i dinler
6. Desteklediği eventleri işler
7. Bildirim davranışını sandbox/log seviyesinde üretir

Bu, projeyi yalnızca "Kafka'ya mesaj basan monolith" olmaktan çıkarıp "ilk bağımsız consumer'ı olan event-driven sistem" seviyesine taşır.

## Sonraki Fazlar İçin Zemin
Faz 10'dan sonra en mantıklı devam başlıkları şunlar:

- in-memory processed event store'u persistent hale getirmek
- gerçek email sender entegre etmek
- consumer retry / DLQ / poison message handling eklemek
- audit consumer yazmak
- analytics consumer yazmak

Yani bundan sonraki işler artık producer tarafını kurmak değil, consumer ekosistemini olgunlaştırmak olacak.

## Kısa Sonuç
Faz 10 ile birlikte:

- ilk gerçek Kafka consumer eklendi
- topic'ten mesaj okuma doğrulandı
- sandbox notification dispatch çalıştı
- offset commit mantığı kuruldu
- basic idempotency zemini eklendi
- malformed payload handling eklendi

Bu faz, Kafka entegrasyonunu producer odaklı bir hazırlık seviyesinden çıkarıp, gerçek event tüketimi olan çok süreçli bir yapıya taşıyan ilk adımdır.
