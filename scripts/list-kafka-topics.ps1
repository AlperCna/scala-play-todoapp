$ErrorActionPreference = "Stop"

docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
if ($LASTEXITCODE -ne 0) {
  throw "Failed to list Kafka topics."
}
