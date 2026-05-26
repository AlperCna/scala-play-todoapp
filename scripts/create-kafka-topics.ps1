$ErrorActionPreference = "Stop"

Write-Host "Creating or verifying Kafka topics..."
docker compose run --rm kafka-init
if ($LASTEXITCODE -ne 0) {
  throw "Failed to create Kafka topics."
}
