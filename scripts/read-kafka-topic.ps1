param(
  [string]$Topic = "todo.events.v1",
  [int]$MaxMessages = 10
)

$ErrorActionPreference = "Stop"

docker compose exec -T kafka kafka-console-consumer `
  --bootstrap-server localhost:9092 `
  --topic $Topic `
  --from-beginning `
  --max-messages $MaxMessages `
  --timeout-ms 5000
if ($LASTEXITCODE -ne 0) {
  throw "Failed to read Kafka topic '$Topic'."
}
