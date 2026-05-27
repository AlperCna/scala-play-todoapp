# todo-analytics-consumer

Bu klasor, `todo.events.v1` topic'inden gelen todo eventlerini okuyup event tabanli analytics projection tablolari ureten ayri consumer moduludur.

## Amac
- `TodoCreated`
- `TodoUpdated`
- `TodoCompleted`
- `TodoDeleted`

eventlerini okuyup uygulamanin kendi operational tablolarina bakmadan analytics verisi olusturmak.

## Urettigi Tablolar
- `tenant_todo_analytics_projection`
- `tenant_todo_analytics_summary`
- `tenant_todo_daily_metrics`

## Temel Kurallar
- Sadece `eventVersion = 1` desteklenir
- `consumer_processed_events` tablosu duplicate event'i ikinci kez projection'a isletmez
- offset commit yalnizca projection guncellemesi basarili olduktan sonra yapilir
- analytics verisi eventlerden turetilir; `todos` tablosuna bakilarak hesaplanmaz

## Nasil Test Edilir?

```powershell
sbt test
```

## Nasil Calistirilir?

```powershell
.\scripts\start-analytics-consumer.ps1
```
