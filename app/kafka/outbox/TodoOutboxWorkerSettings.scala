package kafka.outbox

case class TodoOutboxWorkerSettings(
  enabled: Boolean,
  pollIntervalSeconds: Int,
  batchSize: Int,
  maxAttempts: Int,
  initialRetryDelaySeconds: Int
)
