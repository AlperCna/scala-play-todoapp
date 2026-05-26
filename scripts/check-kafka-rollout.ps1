param(
  [string]$AppUrl = "http://localhost:9000",
  [string]$Topic = "todo.events.v1"
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "== Kafka Compose Status =="
docker compose ps
if ($LASTEXITCODE -ne 0) {
  throw "Failed to read docker compose status."
}

Write-Host ""
Write-Host "== Kafka Topic Check =="
.\scripts\list-kafka-topics.ps1

Write-Host ""
Write-Host "== App Reachability =="
try {
  $response = Invoke-WebRequest -Uri $AppUrl -UseBasicParsing
  Write-Host ("StatusCode: " + $response.StatusCode)
} catch {
  throw "App is not reachable at $AppUrl. Start it with .\scripts\start-kafka-enabled-app.ps1 and run the check again."
}

Write-Host ""
Write-Host "== Outbox Summary =="
.\scripts\check-outbox-summary.ps1
