param(
  [string]$Topic = "todo.events.v1",
  [int]$MaxMessages = 10
)

$ErrorActionPreference = "Stop"

docker compose exec kafka kafka-console-consumer `
  --bootstrap-server localhost:9092 `
  --topic $Topic `
  --from-beginning `
  --max-messages $MaxMessages
if ($LASTEXITCODE -ne 0) {
  throw "Failed to read Kafka topic '$Topic'."
}
