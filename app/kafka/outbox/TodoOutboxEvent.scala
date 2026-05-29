package kafka.outbox

import java.time.LocalDateTime
import java.util.UUID

case class TodoOutboxEvent(
  id: UUID,
  aggregateType: String,
  aggregateId: UUID,
  eventType: String,
  eventVersion: Int,
  tenantId: UUID,
  userId: UUID,
  payloadJson: String,
  headersJson: String,
  status: String,
  attemptCount: Int,
  availableAt: LocalDateTime,
  publishedAt: Option[LocalDateTime],
  lastError: Option[String],
  createdAt: LocalDateTime,
  replayCount: Int,
  lastReplayedAt: Option[LocalDateTime],
  lastReplayedByUserId: Option[UUID]
)
