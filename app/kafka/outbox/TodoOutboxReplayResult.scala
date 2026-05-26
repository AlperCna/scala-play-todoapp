package kafka.outbox

sealed trait TodoOutboxReplayResult

object TodoOutboxReplayResult {
  case object Replayed extends TodoOutboxReplayResult
  case object NotFound extends TodoOutboxReplayResult
  case object NotFailed extends TodoOutboxReplayResult
}
