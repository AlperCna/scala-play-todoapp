# Kafka Rollout Checklist

## Bu Dosya Ne Ise Yarar?
Bu checklist, Kafka entegrasyonunu localden paylasilan ortama veya canliya yakin bir ortama acarken hangi adimlari takip etmemiz gerektigini netlestirir.

Buradaki amac:
- "kod merge oldu" ile yetinmemek
- rollout sirasinda hangi sinyallere bakacagimizi bilmek
- actor tabanli mevcut akis ile Kafka akisinin cakismadan birlikte yasamasini saglamak

## Rollout Oncesi
- `sbt test` temiz mi?
- consumer iskeleti testleri temiz mi?
- Kafka broker/topic erisimi dogrulandi mi?
- `todo.events.v1` topic'i var mi?
- app Kafka acik modda acilabiliyor mu?
- outbox worker loglari beklenen sekilde gorunuyor mu?

## Rollout Sirasi

### 1. Broker ve topic'i dogrula
PowerShell:

```powershell
.\scripts\start-local-kafka.ps1
.\scripts\list-kafka-topics.ps1
```

Beklenen:
- `todo-play-kafka` healthy
- `todo.events.v1` mevcut

### 2. App'i Kafka acik modda ac
PowerShell:

```powershell
.\scripts\start-kafka-enabled-app.ps1
```

Beklenen:
- app `9000` portunda dinliyor
- startup sirasinda injection veya DB config hatasi yok

### 3. Ilk smoke event'i uret
- yeni bir kullanici ile login ol
- yeni bir todo olustur

Beklenen:
- request basarili donmeli
- outbox kaydi olusmali
- worker kaydi `PUBLISHED` yapmali

### 4. Topic'te mesaji gor
PowerShell:

```powershell
.\scripts\read-kafka-topic.ps1 -MaxMessages 5
```

Beklenen:
- olusturdugun event JSON olarak gorunmeli

### 5. Outbox backlog durumunu kontrol et
PowerShell:

```powershell
.\scripts\check-outbox-summary.ps1
```

Beklenen:
- `FAILED` sayisi artmiyor olmali
- `PUBLISHED` sayisi event urettikce artmali

## Ilk Rolloutta Degismeyecekler
- `EmailActor` yerinde kalir
- audit log controller tarafinda kalir
- due date reminder actor yerinde kalir
- login webhook akisina dokunulmaz

Bu neden onemli?
Cunku ilk rolloutta amac her seyi bir anda Kafka'ya tasimak degil.
Amac:
- producer hattini guvenle acmak
- event akisini gozlemlemek
- sonra consumer rollout'unu asamali yapmak

## Gozlenecek Sinyaller
- `PENDING` outbox sayisi
- `FAILED` outbox sayisi
- replay ihtiyaci doguyor mu
- `PUBLISHED` eventler beklenen hizda artiyor mu
- topic'te mesajlar gorunuyor mu
- app request akisi bozulmadan devam ediyor mu

## Rollout Sonrasi Hizli Kontrol
PowerShell:

```powershell
.\scripts\check-kafka-rollout.ps1
```

Bu script sunlari hizli kontrol eder:
- Kafka compose status
- topic varligi
- app cevap veriyor mu
- outbox summary

## Replay Ne Zaman Kullanilir?
Replay ancak su durumda dusunulmeli:
- event `FAILED`
- hata kok nedeni anlasildi
- problem duzeltildi
- duplicate etkisi kabul edilmis veya kontrol altina alinmis

Ilk guvenli kural:
- sadece `FAILED` event replay edilir

## Faz 9'un Mesaji
Bu entegrasyon artik sadece "kod yazildi" seviyesinde degil.
Rollout checklist ile:
- nasil acilacagi
- neyin izlenecegi
- neyin bilerek yerinde birakildigi

netlesmis oldu.
