package dtos

case class OutboxBulkReplayResultResponse(
  replayedCount: Int,
  matchedCount: Int,
  limited: Boolean,
  limit: Int,
  eventType: Option[String],
  createdFrom: Option[String],
  createdTo: Option[String],
  message: String
)
