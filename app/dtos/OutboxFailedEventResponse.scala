package dtos

case class OutboxFailedEventResponse(
  id: String,
  aggregateId: String,
  eventType: String,
  eventVersion: Int,
  attemptCount: Int,
  status: String,
  lastError: Option[String],
  availableAt: String,
  createdAt: String
)
