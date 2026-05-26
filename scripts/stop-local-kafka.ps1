$ErrorActionPreference = "Stop"

Write-Host "Stopping local Kafka services..."
docker compose down -v

if ($LASTEXITCODE -ne 0) {
  throw "Failed to stop local Kafka services."
}
