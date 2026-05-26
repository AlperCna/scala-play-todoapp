# Kafka Phase 6

## Bu Fazda Ne Problemi Cozmeye Calistik?
Faz 5'in sonunda elimizde teknik olarak guclu bir producer hatti vardi:
- event contract
- outbox persistence
- Kafka producer
- worker
- retry ve failure gorunurlugu

Ama hala cok kritik bir eksik vardi:

"Bu sistemi gercek Kafka broker ile nasil calistiracagiz?"

Kod yazmak yetmez.
Dagitik sistemlerde bir seyin gercekten var sayilabilmesi icin:
- broker'i ayaga kaldirabilmen
- topic'i deterministic sekilde yaratabilmen
- uygulamayi dogru config ile baslatabilmen
- mesajin topic'e dustugunu gorebilmen

gerekir.

Faz 6 tam olarak bunu cozer.

Bu fazin konusu yeni business logic degil.
Bu fazin konusu:
- local runtime
- config disiplini
- calistirma rehberi
- "gercek Kafka entegrasyonu"na gecis

## Faz 6'dan Once Nasil Bir Bosluk Vardi?
Faz 1-5 ile su sorulari cevaplamistik:
- hangi eventler uretilir
- event nasil saklanir
- event Kafka'ya nasil cevrilir
- event nasil retry edilir
- worker nasil calisir

Ama sunlar hala belirsizdi:
- local broker nasil ayaga kalkacak
- topic kim yaratacak
- Kafka UI ile nasil bakacagiz
- uygulamayi Kafka acik modda hangi config ile calistiracagiz
- "ben bunu laptop'imda nasil dogrularim?" sorusunun tek bir cevabi yoktu

Bu cok tipik bir bosluktur.
Bir sistem kagit uzerinde tamam gibi gorunur ama calistirma rehberi yoksa ekip icin hala pahali ve kirilgandir.

## Bu Fazda Tam Olarak Ne Yaptik?

### 1. Local Kafka ortami icin `docker-compose.yml` ekledik
Artik repo seviyesinde standart bir local Kafka ortami var.

Compose icinde:
- `kafka`
- `kafka-ui`
- `kafka-init`

servisleri bulunuyor.

Buradaki tasarim kararlari:
- tek brokerli local KRaft kurulum
- plaintext local erisim
- `auto.create.topics` kapali
- topic yaratimi ayri helper akisi ile deterministic

Bu neden onemli?
Cunku auto-create acik oldugunda bazen typo ile yanlis topic bile acilabilir.
Biz localde bile topic ismini kontrollu tutmak istedik.

### 2. Topic bootstrap helper ekledik
`kafka-init` adinda tek seferlik helper servis ekledik.

Bu servis:
- broker saglikli hale gelince calisir
- `todo.events.v1` topic'ini `--if-not-exists` ile yaratir
- describe ederek sonucu gorunur hale getirir

Bu bize iki fayda verir:
- topic olusumu deterministic olur
- komutu tekrar calistirmak guvenli olur

### 3. PowerShell scriptleri ekledik
Windows gelistirme akisini kolaylastirmak icin scriptler eklendi:
- `scripts/start-local-kafka.ps1`
- `scripts/create-kafka-topics.ps1`
- `scripts/list-kafka-topics.ps1`
- `scripts/read-kafka-topic.ps1`

Bu scriptlerin amaci su:
- uzun docker compose komutlarini ezberletmemek
- herkesin ayni sekilde broker baslatmasini saglamak
- topic dogrulama ve mesaj okuma adimlarini netlestirmek

### 4. Ayri local Kafka config ornegi ekledik
Yeni dosya:
- `conf/kafka-local.conf.example`

Bu dosyada:
- `include "application.conf"` ile mevcut app config korunuyor
- sadece Kafka ile ilgili override'lar aciliyor
- `kafka.enabled = true` hale geliyor

Bu neden guzel?
Cunku:
- kullanicinin `conf/application.conf` dosyasi zaten lokal farklar tasiyor
- o dosyaya dokunmak istemiyoruz
- ama ayni zamanda tek komutla Kafka acik modda calismak istiyoruz

### 5. Runbook yazdik
Yeni dosya:
- `docs/kafka-local-runbook.md`

Bu dokuman:
- broker nasil baslatilir
- topic nasil dogrulanir
- app Kafka acik modda nasil kosulur
- outbox tablosunda neye bakilir
- topic'ten mesaj nasil okunur
- hata durumunda neler kontrol edilir

gibi pratik adimlari anlatir.

Yani Faz 6 sadece kod degil, operasyonel rehber de uretir.

### 6. README'yi bu yeni akisla uyumlu hale getirdik
README artik:
- projenin sadece genel tanimini vermiyor
- Kafka local rehberine de yol gosteriyor

Bu kucuk gibi gorunur ama repo'ya yeni gelen biri icin cok degerlidir.

### 7. `.gitignore` icine local Kafka override yolu ekledik
Eklenen satir:
- `conf/kafka-local.conf`

Bu su anlama gelir:
- repo icinde paylasilan ornek dosya var
- ama ileride kisi kendine ozel gercek lokal override olusturursa bunu yanlislikla commit etmez

Bu da lokal konfigurasyon ile repo sabitleri arasinda saglikli sinir cizer.

## Teoride Burada Neyi Ogreniyoruz?

### "Kod hazir" ile "sistem calisiyor" ayni sey degildir
Kafka gibi sistemlerde sadece producer kodu yazmak yeterli degildir.
Su dort sey birlikte gerekir:
- uygulama kodu
- broker ortami
- topic yonetimi
- calistirma rehberi

Faz 6 bu yuzden onemlidir.
Cunku burada kodun etrafindaki runtime dunyasini tamamliyoruz.

### Neden auto-create'e guvenmedik?
Auto-create localde kolay gorunur ama su riskleri getirir:
- yanlis topic ismi fark edilmeden olusur
- test ile production topic davranisi arasinda fark buyur
- topology karari kod disinda rastgele hale gelir

Deterministic create daha disiplinlidir.

### Neden ornek config ayri dosyada?
Lokal ekip ayarlari genelde hassas ve degisken olur.
Var olan `application.conf` kullaniciya ait farklar tasiyorsa ona dokunmak repo isini zorlastirir.

Bu yuzden iyi pratik:
- paylasilan reference dosya
- opsiyonel local override dosyasi
- app'in default davranisini bozmamak

### Neden runbook ayri dokuman olarak yazildi?
Cunku build bilgisi ile "calistirma bilgisi" ayni sey degildir.

Bir sistemin su ikisine de ihtiyaci vardir:
- mimari rapor
- operasyonel calistirma rehberi

`kafka-phase-6.md` daha cok neden-sonuc ve tasarim anlatir.
`kafka-local-runbook.md` ise "adim adim ne yapacagim?" sorusunu cevaplar.

## Bu Fazda Eklenen Yapilar Ne Is Yapiyor?

### `docker-compose.yml`
Local Kafka runtime'ini standartlastirir.

### `conf/kafka-local.conf.example`
Uygulamayi Kafka acik modda baslatmak icin paylasilan override ornegidir.

### `scripts/start-local-kafka.ps1`
Broker + UI + topic bootstrap akisini tek komutta toplar.

### `scripts/create-kafka-topics.ps1`
Topic bootstrap'i tekrar calistirmak icindir.

### `scripts/list-kafka-topics.ps1`
Topic'in gercekten olustugunu dogrular.

### `scripts/read-kafka-topic.ps1`
Topic'teki mesajlari hizli sekilde okumayi saglar.

### `docs/kafka-local-runbook.md`
Gercek local entegrasyonun adim adim rehberidir.

## Testte Neyi Kanitladik?

### 1. Mevcut uygulama testleri tekrar kosuldu
Calistirilan komut:

```powershell
sbt test
```

Beklenti:
- Faz 6 dosyalari mevcut Scala kodunu bozmamali
- onceki fazlarin testleri temiz gecmeli

### 2. Compose dosyasi yapisal olarak dogrulandi
Mumkunse su komutla:

```powershell
docker compose config
```

Bu dogrulama neden degerli?
Cunku runtime tarafinda syntax bozuksa kod testlerinin gecmesi tek basina yeterli olmaz.

### 3. Manuel local entegrasyon akisi dokumante edildi
Bu fazda tum runtime dogrulamayi unit test ile yakalamak dogru degil.
Burada asil olan sey adimlari deterministic hale getirmek:
- broker'i baslat
- topic'i olustur
- uygulamayi Kafka acik modda kos
- outbox status'unu kontrol et
- topic'te mesaji gor

Yani Faz 6'nin en kritik testi yarisi kod, yarisi operasyonel dogrulamadir.

## Bu Fazdan Sonra Sistem Hangi Seviyeye Geldi?
Bu noktada sistem artik sadece "Kafka'ya hazir kod" degil.
Ayni zamanda:
- local broker ile calistirilabilir
- topic'i kontrollu sekilde yaratilabilir
- Kafka acik modda kosturulabilir
- outbox publish akisina bakilabilir
- topic'te gercek mesajlar gorulebilir

Yani producer-outbox-worker hattinin localde gercek broker ile hayata gecmesi icin gerekli cati tamamlanmis olur.

## Hala Ne Eksik?
Faz 6 bittiginde bile hala future work var:
- gercek consumer servisleri
- idempotent consumer davranisi
- DLQ handling
- replay araci
- daha zengin monitoring endpoint'leri
- local yerine paylasilan dev/stage rollout rehberi

Yani Faz 6 producer tarafinin runtime acilisidir.
Ama tam event-driven ekosistem icin sonraki fazlar yine gereklidir.
