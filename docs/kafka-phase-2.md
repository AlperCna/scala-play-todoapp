# Kafka Phase 2

## Bu Fazda Ne Problemi Cozmeye Calistik?
Faz 1'de event uretmeyi ogrendik.
Ama Faz 1'den sonra hala buyuk bir risk vardi:

- todo verisi DB'ye yazilir
- event sonradan bir sebeple gonderilemez
- sistemin asil verisi vardir
- ama event kaybolur

Bu neden kotu?
Cunku ileride Kafka'yi su isler icin kullanmak istiyoruz:
- notification
- analytics
- audit
- baska servislerle entegrasyon

Eger event kaybolursa bu sistemler eksik veri gorur.
Yani kullanici todo olusturmustur ama analytics bunu hic ogrenemez.

Faz 2'de coztugumuz sey tam olarak buydu:

"Event'i daha Kafka'ya basmadan once guvenli sekilde nasil saklariz?"

Cevap:
- outbox pattern

## Outbox Pattern Nedir? Cok Basit Anlatim
Outbox pattern'in mantigi su:

Bir business olay oldugunda:
- asil veriyi DB'ye yaz
- ayni anda "gonderilecek event" kaydini da DB'ye yaz

Yani daha Kafka'ya gondermeden once event'i veritabaninda saklarsin.

Boylece:
- Kafka o anda yoksa bile event kaybolmaz
- sonra bir worker gelir ve bu kayitlari Kafka'ya yollar

Kisacasi:
- Faz 1'de "event var"
- Faz 2'de "event guvenli saklaniyor"

## Neden "Ayni Transaction" Bu Kadar Onemli?
Burasi Faz 2'nin kalbi.

Asil istedigimiz garanti su:

Ya ikisi birden olsun:
- todo kaydi
- outbox kaydi

Ya da ikisi de olmasin.

Yani sunu istemiyoruz:
- todo var ama event yok

Bu durumda sistem yarim kalmis olur.

Bu yuzden todo yazimi ve outbox insert ayni transaction'da olmali.

Transaction ne saglar?
- iki yazim da basariliysa commit
- bir tanesi fail olursa rollback

Rollback ne demek?
- sanki hic yazilmamis gibi geri almak

Yani Faz 2'nin teorik olarak en onemli kazanci:
"business veri ile event kaydi artik ayni kaderi paylasiyor."

## Bu Projede Tam Olarak Ne Yaptik?

### 1. Outbox tablosu ekledik
Yeni evolution:
- `conf/evolutions/default/4.sql`

Eklenen tablo:
- `todo_event_outbox`

Bu tabloya su tarz veriler yaziliyor:
- bu event hangi todo icin
- event tipi ne
- event versiyonu ne
- tenant kim
- user kim
- payload ne
- header bilgileri ne
- status ne
- kac kere denendi
- ne zaman olustu

Bu tabloyu bir nevi "Kafka'ya gidecekler bekleme alani" gibi dusunebilirsin.

Su an eventler burada bekliyor.
Henuz Kafka'ya gitmiyorlar.

### 2. Outbox modelini ekledik
Yeni siniflar:
- `TodoOutboxEvent`
- `TodoOutboxStatus`

Burada DB tablosunu uygulama tarafinda tipli hale getirdik.

Status degerleri:
- `PENDING`
- `PUBLISHED`
- `FAILED`

Su anda aktif kullandigimiz durum:
- `PENDING`

Cunku Faz 2'de event sadece yaziliyor, henuz publish edilmiyor.

### 3. Domain event'ten outbox kaydi ureten factory ekledik
Yeni sinif:
- `TodoOutboxEventFactory`

Faz 1'de event envelope uretmistik.
Faz 2'de bu event'i alip DB'ye yazilabilir bir satira cevirmemiz gerekiyordu.

Bu factory'nin isi su:
- event'i al
- payload'i string hale getir
- header bilgilerini hazirla
- ilk status'u `PENDING` yap
- outbox nesnesi uret

Bu da su ayirimi sagliyor:
- service business olayi bilir
- factory DB'ye uygun outbox nesnesini uretir

### 4. Outbox repository ekledik
Yeni siniflar:
- `TodoOutboxRepository`
- `TodoOutboxRepositoryImpl`

Bu katmanin gorevi:
- outbox kaydi yazmak
- aggregate id ile kayitlari okumak
- status bazli sayi almak

Bu fazda bu repository'yi hem sistem akisi icin hem de testlerde dogrulama yapmak icin kullandik.

### 5. En onemli katman: transaction command repository
Yeni siniflar:
- `TodoOutboxCommandRepository`
- `TodoOutboxCommandRepositoryImpl`

Bu fazin en kritik kodu burada.

Bu katman su isi yapiyor:
- todo create + outbox insert ayni transaction
- todo update + outbox insert ayni transaction
- todo delete + outbox insert ayni transaction

Neden bunu ayri bir katmanda yaptik?
Cunku mevcut `TodoRepositoryImpl` daha cok normal CRUD gibi calisiyordu ve her metot kendi baglantisinda is yapiyordu.

Ama bizim ihtiyacimiz sunun garantisi:
- todo ve outbox birlikte yazilsin

Bu yuzden bunu ayri bir "command repository" mantigina aldik.

### 6. `TodoServiceImpl` degisti
Faz 1'de `TodoServiceImpl` akisi suydi:
- event uret
- publisher'a ver
- publisher `NoOp` oldugu icin bir sey yapma

Faz 2'de akis su oldu:
- event uret
- outbox event'e cevir
- todo + outbox birlikte DB'ye yaz

Bu su demek:
artik event uygulama icinde sadece hazirlanip kaybolmuyor
artik DB'de kalici hale geliyor

### 7. Semantik yine korunuyor
Faz 2'de event mantigini degistirmedik.
Hala:
- `createTodo` -> `TodoCreated`
- `updateTodo` -> `TodoUpdated`
- `deleteTodo` -> `TodoDeleted`
- `toggleTodo` false -> true ise `TodoCompleted`

Yani Faz 2'nin isi event'in anlami degil, event'in guvenli saklanmasiydi.

## Faz 2'den Once ve Sonra Fark Ne?

### Faz 2'den once
- event olusuyordu
- ama gercek yere yazilmiyordu
- sadece akista var gibiydi

### Faz 2'den sonra
- event olusuyor
- outbox tablosuna yaziliyor
- todo ile ayni transaction icinde kayda giriyor

Yani Faz 2 bize "guvenilirlik" kazandirdi.

## Bu Fazda Neyi Bilerek Yapmadik?
Burasi da yine onemli.

Faz 2'de henuz sunlari yapmadik:
- Kafka producer baglamadik
- outbox worker yazmadik
- `PUBLISHED` durumuna geciren kod yazmadik
- retry/backoff eklemedik
- failed event replay akisi eklemedik

Yani eventler artik DB'de guvenli sekilde duruyor ama henuz disari gitmiyor.

Bu normal. Cunku Faz 2'nin isi gondermek degil, kaybetmeden tutmak.

## Testte Neyi Kanitladik?
Faz 2 testleri bu isin en degerli kismi.
Cunku transaction isleri "bakinca dogru gibi duran" ama sessizce bozulabilen seylardir.

Bu yuzden sadece derleme yetmez; davranisi test etmek gerekir.

### 1. Outbox factory testi
Eklenen test:
- `TodoOutboxEventFactorySpec`

Bu test neyi kanitliyor?
- domain event outbox nesnesine cevriliyor mu
- `PENDING` status ile mi basliyor
- `attemptCount` sifir mi
- payload/header bilgileri dolu mu

Bu test bize "event -> outbox" cevirisinin saglam oldugunu gosteriyor.

### 2. Create + outbox birlikte yaziliyor mu?
Eklenen test:
- `TodoOutboxCommandRepositorySpec`

Ilk senaryo:
- todo olustur
- outbox olustur
- ikisi de DB'de var mi bak

Bu test su guveni veriyor:
- create islemi event kaydiyla beraber yaziliyor

### 3. Update yeni outbox olusturuyor mu?
Ikinci senaryo:
- todo once olusturuluyor
- sonra update ediliyor
- aggregate icin iki outbox kaydi var mi bakiliyor

Bu test su guveni veriyor:
- sistem yeni olaylar icin yeni outbox satiri aciyor

### 4. En kritik test: rollback
Ucuncu senaryo:
- outbox insert'i bilerek bozuyoruz
- sonra create akisini cagiriyoruz

Beklenti:
- todo da yazilmamali
- outbox da yazilmamali

Bu testin anlami cok buyuk:
- transaction garantisi gercekten calisiyor
- yani sistem yari yolda "todo var ama event yok" durumuna dusmuyor

Bu Faz 2'nin teorik vaadini pratikte kanitlayan test bu.

## Faz 2 Sonunda Calistirdigimiz Komut
Asagidaki komut calistirildi:

```powershell
sbt test
```

Sonuc:
- toplam 10 test gecti
- 0 hata

Bu testler icinde:
- Faz 1 testleri
- Faz 2 outbox testleri
- mevcut uygulama testleri

birlikte gecti.

Bu bize su guveni veriyor:
- yeni outbox yapisi derleniyor
- DI wiring bozulmadi
- evolutions ile uyumlu
- transaction davranisi test edildi

## Faz 2 Bize Ne Kazandirdi?
Faz 2'den sonra proje sunu diyebiliyor:

"Bir todo degisikligi olduysa, ona ait event kaydi da DB'de vardir."

Bu cok buyuk bir adim.
Cunku event-driven sistemlerde en zor konulardan biri "olay kaybetmemek"tir.

Faz 2 ile birlikte:
- event artik gecici bir nesne degil
- kalici bir DB kaydi
- business veri ile ayni kaderi paylasiyor

## Hala Ne Eksik?
Sunlar hala eksik:
- outbox'taki eventleri Kafka'ya basan producer
- bu kayitlari tarayan worker
- publish sonrasi `PUBLISHED` guncellemesi
- retry/backoff
- failed event handling

Yani Faz 2'nin sonunda elimizde "gonderilmeye hazir event deposu" var.

## Faz 3 ve Faz 4 Neden Gerekli?

### Faz 3
Gercek Kafka producer eklenecek.
Yani artik "DB'de event var" demekle kalmayacagiz, bu event'i Kafka mesajina donusturecegiz.

### Faz 4
Outbox worker gelecek.
Yani DB'deki `PENDING` kayitlari alip Kafka'ya yollayan runtime mekanizma kurulacak.

Kisacasi:
- Faz 1: event dili
- Faz 2: event guvenligi
- Faz 3: Kafka'ya basma yetenegi
- Faz 4: bunu otomatik yapan calisan mekanizma
