package kafka.outbox

case class TodoOutboxBulkReplayResult(
  matchedCount: Int,
  replayedCount: Int,
  limited: Boolean,
  limit: Int
)
