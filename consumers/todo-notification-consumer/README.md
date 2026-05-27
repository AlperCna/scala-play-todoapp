# todo-notification-consumer

Bu klasor, ana Play uygulamasindan fiziksel olarak ayri duran ilk Kafka consumer iskeletidir.

Amaci:
- `todo.events.v1` topic'inden gelen olaylari yorumlamak
- ilk asamada sadece bildirim acisindan anlamli eventleri ele almak
- idempotency ve versiyon kontrolu gibi consumer tarafi kurallarini kod seviyesinde gostermek

Bu klasor neden ayri?
- consumer mantigini monolith actor yapisinin icine gommek istemiyoruz
- ileride bunu ayri deploy edilen bir servis haline getirmek daha kolay olsun istiyoruz

## Ilk Kapsam
Bu iskelet sadece su eventleri destekler:
- `TodoCreated`
- `TodoCompleted`

Bilerek desteklemiyoruz:
- `TodoUpdated`
- `TodoDeleted`

Bunun nedeni su:
- ilk business ihtiyac bildirimdir
- her update icin bildirim gondermek gereksiz gurultu yaratabilir
- delete senaryosu daha sonra ayri karar gerektirebilir

## Consumer Kurallari
- `eventVersion` su an sadece `1` destekler
- `eventId` duplicate gelirse islenmez
- consumer offset commit mantigi bu iskelette gercek Kafka client ile yazilmadi, ama karar seviyesinde "yalnizca basarili islemden sonra" kabul edilir
- varsayilan dispatch mode `sandbox` dusunulmustur

## Nasil Test Edilir?
Bu klasorun icinde:

```powershell
sbt test
```

Bu testler sunlari dogrular:
- producer'dan gelen JSON event parse ediliyor mu
- duplicate event ignore ediliyor mu
- unsupported version ignore ediliyor mu
- unsupported event type ignore ediliyor mu
- desteklenen eventler icin bildirim komutu olusuyor mu

## Nasil Calistirilir?
Root repo icinden:

```powershell
.\scripts\start-notification-consumer.ps1
```

Bu komut consumer'i su davranisla kaldirir:
- Kafka topic: `todo.events.v1`
- group id: `todo-notification-consumer-v1`
- varsayilan dispatch mode: `sandbox`
- offset commit: yalnizca mesaj handle edildikten sonra

Ilk implementasyonda bildirim gonderimi gercek email yerine log/sandbox davranisidir.
