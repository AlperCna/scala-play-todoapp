# Kafka Phase 8

## Bu Fazda Ne Problemi Cozmeye Calistik?
Faz 7'nin sonunda artik eventleri ureten producer tarafi da vardi, ilk consumer iskeleti de vardi.
Ama production'a yaklasan her event sisteminde bir sonraki kritik soru aynidir:

"Bir event takilirsa bunu nasil gorecegiz ve nasil geri oynatacagiz?"

Outbox mekanizmasi `FAILED` kayitlari tutuyordu.
Bu iyi bir baslangicti.
Ama tek basina yeterli degildi.

Cunku operasyon tarafinda su ihtiyaclar dogar:
- su an tenant bazinda kac `FAILED` event var
- hangi eventler patlamis
- en son hata neymis
- bu eventleri tekrar kuyruga almak istersek nasil yapacagiz

Faz 8'in amaci tam olarak bu boslugu kapatmakti.

Bu fazda:
- failed outbox kayitlarini listeleme
- summary bilgisini tenant seviyesinde verme
- sadece `FAILED` durumundaki eventler icin replay
- bu davranislari testlerle dogrulama

ekledik.

## Bu Fazda Ne Yaptik?

### 1. Repository'yi operasyonel ihtiyaclar icin genislettik
`TodoOutboxRepository` yeni yetenekler kazandi:
- `countByStatusAndTenant`
- `findFailedByTenantPaged`
- `findById`
- `findByIdAndTenant`
- `resetForReplay`

Bu neden onemli?
Cunku Faz 4-5'te repository daha cok publish worker ihtiyaclarina hizmet ediyordu.
Faz 8 ile birlikte repository artik operasyon ve admin ihtiyaclarini da tasiyor.

### 2. Replay icin durum semantigi netlestirildi
Yeni sonuc tipi:
- `TodoOutboxReplayResult`

Durumlar:
- `Replayed`
- `NotFound`
- `NotFailed`

Bu neden degerli?
Cunku replay istegi her zaman basarili olmak zorunda degil.
Ve biz bu farkli sonuc tiplerini acik ve okunur yapmak istedik.

### 3. Outbox operasyon servisi ekledik
Yeni servis:
- `TodoOutboxOperationsService`

Bu servis:
- tenant bazli summary uretir
- tenant bazli failed event listesini sayfalar
- tek bir failed event'i replay icin yeniden `PENDING` durumuna alir

Bu servis Faz 8'in asil is mantigini tasir.

### 4. Replay mantigini kontrollu tuttuk
Replay su sekilde calisir:
- event bulunur
- tenant kontrolu yapilir
- sadece `FAILED` ise replay edilir
- replay sirasinda event tekrar `PENDING` olur
- `attempt_count` sifirlanir
- `last_error` temizlenir
- `published_at` temizlenir
- `available_at` su an olacak sekilde ileri alinmaz, hemen publish edilebilir olur

Bu sayede worker sonraki dongude bu kaydi yeniden alabilir.

### 5. Admin tarafina hafif JSON endpoint'leri ekledik
Yeni endpoint'ler:
- `GET /admin/outbox/summary`
- `GET /admin/outbox/failed`
- `POST /admin/outbox/:id/replay`

Neden JSON?
Cunku bu fazin hedefi once isletilebilirlikti.
UI yazmadan once:
- endpoint semantigi net olsun
- servis mantigi otursun
- API ile operasyon yapabilelim

UI istersek daha sonra bu yuzlerin ustune eklenebilir.

### 6. DTO'lar ekledik
Yeni response tipleri:
- `OutboxFailedEventResponse`
- `OutboxFailedEventPageResponse`
- `OutboxReplayResultResponse`

Bu tipler admin endpoint'lerinin ne dondurecegini daha net hale getiriyor.

### 7. Faz 8 testlerini ekledik
Yeni test:
- `TodoOutboxOperationsServiceSpec`

Bu testler su sorulari cevapliyor:
- tenant bazli summary dogru mu
- failed event page dogru olusuyor mu
- failed event replay ediliyor mu
- failed olmayan event replay'e izin vermiyor mu

## Teoride Burada Neyi Ogreniyoruz?

### Neden replay gerekir?
Outbox `FAILED` kaydi demek su olabilir:
- Kafka gecici olarak yoktu
- config kisa sure bozuldu
- topic erisimi gecici fail oldu

Bu durumda problemi duzelttikten sonra event'i tekrar publish etmek isteyebilirsin.

Replay tam olarak bunun operasyonel cevabidir.

### Neden her event replay edilemez?
Eger her status'teki event replay edilirse:
- duplicate publish riski buyur
- `PUBLISHED` event tekrar basinabilir
- sistemin durumunu anlamak zorlasir

Bu yuzden ilk guvenli kural:
- sadece `FAILED` event replay edilir

Bu sade ama saglam bir baslangictir.

### Neden tenant bazli filtreleme onemli?
Bu uygulama multi-tenant.
Dolayisiyla bir tenant admin'inin:
- kendi failed eventlerini gormesi
- baska tenant eventlerine erismemesi

gerekir.

Faz 8'de bu nedenle listeleme ve replay akisi tenant bazli yazildi.

### Neden UI yerine once JSON endpoint?
Operations icin once semantigin dogru olmasi gerekir.
UI daha sonra daha rahat degisir.

JSON endpoint once su faydalari verir:
- Postman/curl ile kolay test
- dashboard'a daha sonra rahat baglama
- servis davranisinin kolay izlenmesi

## Testte Neyi Kanitladik?

### 1. Tenant summary testi
`summaryForTenant` icin:
- pending
- published
- failed

sayilarinin dogru toplandigi test edildi.

### 2. Failed page testi
Failed eventlerin:
- dogru tenant'tan cekildigi
- response'a dogru maplendigi

dogrulandi.

### 3. Replay mutlu yol testi
`FAILED` bir event icin:
- replay sonucu `Replayed`
- repository reset cagrisinin gittigi

ispatlandi.

### 4. Replay reddi testi
`PUBLISHED` gibi failed olmayan bir event icin:
- replay sonucu `NotFailed`
- reset cagrisinin gitmedigi

dogrulandi.

## Bu Fazdan Sonra Sistem Hangi Seviyeye Geldi?
Artik sadece event ureten ve publish eden bir sistemimiz yok.
Ayni zamanda:
- failed eventleri gorebilen
- tenant bazli backlog'u sayabilen
- manuel replay yapabilen

bir operasyon yuzeyimiz de var.

Bu, Kafka entegrasyonunu ciddi sekilde daha isletilebilir hale getirir.

## Hala Ne Eksik?
Faz 8 sonrasinda hala future work var:
- replay icin UI
- audit trail uzerinde replay aksiyonu loglamak
- daha zengin monitoring ekranlari
- consumer tarafi icin DLQ handling
- otomatik alarm/metric entegrasyonu

Yani Faz 8 replay ve failed visibility temelini atti.
Ama operasyonel ekosistemi tamamen bitirmedi.
