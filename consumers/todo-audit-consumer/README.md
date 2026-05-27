# todo-audit-consumer

Bu klasor, `todo.events.v1` topic'inden gelen todo eventlerini okuyup `audit_logs` tablosuna event tabanli audit kaydi ureten ayri consumer modülüdür.

## Amac
- `TodoCreated`
- `TodoUpdated`
- `TodoCompleted`
- `TodoDeleted`

eventlerini Kafka'dan okuyup audit kaydina cevirmek.

## Temel Kurallar
- Sadece `eventVersion = 1` desteklenir
- `consumer_processed_events` tablosu ile duplicate event ikinci kez audit kaydi uretmez
- offset commit yalnizca audit yazimi basarili olduktan sonra yapilir
- mevcut controller audit log yapisi korunur; bu consumer ona paralel calisir

## Nasil Test Edilir?

```powershell
sbt test
```

## Nasil Calistirilir?

```powershell
.\scripts\start-audit-consumer.ps1
```
