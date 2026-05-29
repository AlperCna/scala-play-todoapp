package kafka.outbox

import java.time.LocalDateTime
import java.util.UUID

case class TodoOutboxReplayLog(
  id: UUID,
  outboxId: UUID,
  tenantId: UUID,
  requestedByUserId: UUID,
  eventType: String,
  replayMode: String,
  filterSummary: Option[String],
  replayedAt: LocalDateTime,
  createdAt: LocalDateTime
)
