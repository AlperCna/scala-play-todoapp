package kafka.outbox

import kafka.events.DomainEventEnvelope
import play.api.libs.json.Json

import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}

@Singleton
class TodoOutboxEventFactory @Inject()() {

  private val AggregateType = "todo"

  def fromEnvelope(event: DomainEventEnvelope): TodoOutboxEvent = {
    val now = LocalDateTime.now(ZoneOffset.UTC)

    TodoOutboxEvent(
      id = UUID.randomUUID(),
      aggregateType = AggregateType,
      aggregateId = event.entityId,
      eventType = event.eventType,
      eventVersion = event.eventVersion,
      tenantId = event.tenantId,
      userId = event.userId,
      payloadJson = Json.stringify(event.payload),
      headersJson = Json.stringify(
        Json.obj(
          "eventType" -> event.eventType,
          "eventVersion" -> event.eventVersion,
          "tenantId" -> event.tenantId.toString,
          "userId" -> event.userId.toString,
          "entityType" -> event.entityType,
          "correlationId" -> event.correlationId
        )
      ),
      status = TodoOutboxStatus.Pending,
      attemptCount = 0,
      availableAt = now,
      publishedAt = None,
      lastError = None,
      createdAt = now
    )
  }
}
