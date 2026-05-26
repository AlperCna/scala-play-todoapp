package kafka.events

import play.api.libs.json.JsObject

import java.time.Instant
import java.util.UUID

case class DomainEventEnvelope(
  eventId: UUID,
  eventType: String,
  eventVersion: Int,
  occurredAt: Instant,
  tenantId: UUID,
  userId: UUID,
  entityType: String,
  entityId: UUID,
  correlationId: Option[String],
  payload: JsObject
)
