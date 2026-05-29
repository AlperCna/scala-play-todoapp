package dtos

case class OutboxReplayLogResponse(
  id: String,
  outboxId: String,
  requestedByUserId: String,
  eventType: String,
  replayMode: String,
  filterSummary: Option[String],
  replayedAt: String,
  createdAt: String
)
