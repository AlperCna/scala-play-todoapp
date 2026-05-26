# Kafka Local Runbook

## Bu Dosya Ne Is Yapiyor?
Bu runbook, projedeki mevcut outbox + producer + worker hattini gercek bir local Kafka broker ile nasil calistiracagimizi anlatiyor.

Buradaki amac sadece "Docker ayaga kalksin" degil.
Asil amac su akis gercekten calisiyor mu bunu gormek:

1. uygulama todo degisikligi yapar
2. event outbox tablosuna yazar
3. worker bu kaydi alir
4. Kafka broker'a publish eder
5. outbox status `PUBLISHED` olur
6. mesaj topic icinde gorunur

## Bu Fazdan Once Durum Neydi?
Kod seviyesinde zaten hazirdik:
- domain event contract vardi
- outbox pattern vardi
- Kafka producer vardi
- worker vardi

Ama gercek calisan ortam yoktu:
- broker yoktu
- topic deterministic sekilde yaratilmis degildi
- Kafka mesajini localde gormek kolay degildi

Bu runbook bu eksigi kapatir.

## Gereksinimler
- Docker Desktop veya Docker Engine
- `docker compose`
- `sbt`
- Projenin kullandigi veritabani ayarlari hazir olmali

## Local Kafka Ortamini Ayaga Kaldirma

### 1. Broker ve UI'yi baslat
PowerShell:

```powershell
.\scripts\start-local-kafka.ps1
```

Bu komut:
- `kafka` broker container'ini baslatir
- `kafka-ui` arayuzunu baslatir
- `todo.events.v1` topic'ini deterministic sekilde yaratir

### 2. Topic'leri listele
PowerShell:

```powershell
.\scripts\list-kafka-topics.ps1
```

Beklenen cikti icinde su topic gorunmeli:

```text
todo.events.v1
```

### 3. Kafka UI'yi ac
Tarayicidan:

- [Kafka UI](http://localhost:8085)

Burada `todo-play-local` cluster'ini ve `todo.events.v1` topic'ini gormelisin.

## Uygulamayi Kafka Acik Modda Calistirma

### 1. Local Kafka config ile uygulamayi baslat
PowerShell:

```powershell
sbt -Dconfig.file=conf/kafka-local.conf.example run
```

Bu config neden ayri?
- `conf/application.conf` kullanicinin lokal ayarlarini tasiyor
- onu bozmak istemiyoruz
- bu dosya sadece Kafka override'larini aciyor

### 2. Todo islemi olustur
Uygulamadan:
- yeni bir todo olustur

Bu islemden sonra beklenen sey:
- todo kaydi yazilir
- outbox tablosuna `PENDING` kayit duser
- worker bir sonraki poll araliginda bunu alip Kafka'ya basar
- status `PUBLISHED` olur

## Outbox Durumunu Dogrulama
SQL tarafinda su sorgu ile son eventleri gorebilirsin:

```sql
SELECT TOP 20
    id,
    event_type,
    status,
    attempt_count,
    available_at,
    published_at,
    last_error,
    created_at
FROM todo_event_outbox
ORDER BY created_at DESC;
```

Beklenen:
- yeni event once `PENDING` olarak gorunebilir
- worker calistiktan sonra `PUBLISHED` olmali

Eger `FAILED` gorursen:
- `last_error` kolonuna bak
- broker acik mi
- topic var mi
- `kafka.enabled=true` ile mi calisiyorsun

## Kafka Mesajini Okuma
PowerShell:

```powershell
.\scripts\read-kafka-topic.ps1 -MaxMessages 5
```

Bu komut topic'ten ilk mesajlari okumaya calisir.

Beklenen:
- `TodoCreated`, `TodoUpdated`, `TodoDeleted` veya `TodoCompleted` event payload'i JSON olarak gorunmeli

## Sadece Topic Bootstrap Yeniden Calistirma
Eger broker zaten acik ama topic bootstrap'i tekrar calistirmak istiyorsan:

```powershell
.\scripts\create-kafka-topics.ps1
```

Bu komut `--if-not-exists` kullandigi icin tekrar calistirilabilir.

## Kafka Kapali Mod Dogrulamasi
Kafka kapali mod hala calismaya devam etmeli.

Normal mod:

```powershell
sbt run
```

Bu durumda:
- uygulama acilmali
- todo akisi bozulmamali
- worker Kafka disabled oldugu icin publish denememeli

Bu bize iki onemli modu dogrular:
- Kafka olmayan gelistirme modu
- Kafka acik local entegrasyon modu

## Sorun Giderme

### Broker ayaga kalkmiyorsa
- `docker ps` ile container'lari kontrol et
- `docker compose logs kafka` ile broker loglarina bak

### Topic gorunmuyorsa
- `.\scripts\create-kafka-topics.ps1` komutunu tekrar calistir
- `.\scripts\list-kafka-topics.ps1` ile tekrar dogrula

### Outbox `PUBLISHED` olmuyorsa
- uygulamayi Kafka acik config ile mi baslattigini kontrol et
- `todo_event_outbox.last_error` kolonuna bak
- `docker compose logs kafka` ile broker hata veriyor mu kontrol et

### UI aciliyor ama mesaj yoksa
- todo olayi gercekten olustu mu kontrol et
- outbox'ta yeni satir var mi kontrol et
- status `PUBLISHED` oldu mu kontrol et

## Bu Fazla Neyi Kazandik?
Bu runbook ile artik sadece kod yazmis olmuyoruz.
Ayni zamanda bu kodu localde gercek broker ile calistirip gozleyebilecegimiz net bir yolumuz oluyor.

Bu da Faz 6'nin ana hedefidir:
- "Kafka'ya hazir kod"dan
- "Kafka ile gercekten calisan local entegrasyon"a gecmek
