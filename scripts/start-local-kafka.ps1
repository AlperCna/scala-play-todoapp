param(
  [switch]$SkipTopicBootstrap
)

$ErrorActionPreference = "Stop"

Write-Host "Starting local Kafka broker and Kafka UI..."
docker compose up -d kafka kafka-ui
if ($LASTEXITCODE -ne 0) {
  throw "Failed to start Kafka services."
}

if (-not $SkipTopicBootstrap) {
  Write-Host "Creating local Kafka topics..."
  docker compose run --rm kafka-init
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to create Kafka topics."
  }
}

Write-Host "Local Kafka services are ready."
