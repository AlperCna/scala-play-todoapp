package kafka.outbox

import kafka.events.DomainEventEnvelope
import play.api.libs.json.{JsObject, JsValue, Json}

import java.time.ZoneOffset
import java.util.UUID
import javax.inject.{Inject, Singleton}
@Singleton
class TodoOutboxEnvelopeFactory @Inject()() {

  def toEnvelope(outboxEvent: TodoOutboxEvent): DomainEventEnvelope = {
    val payload = Json.parse(outboxEvent.payloadJson).as[JsObject]
    val headers = Json.parse(outboxEvent.headersJson)

    DomainEventEnvelope(
      eventId = outboxEvent.id,
      eventType = outboxEvent.eventType,
      eventVersion = outboxEvent.eventVersion,
      occurredAt = outboxEvent.createdAt.toInstant(ZoneOffset.UTC),
      tenantId = outboxEvent.tenantId,
      userId = outboxEvent.userId,
      entityType = outboxEvent.aggregateType,
      entityId = outboxEvent.aggregateId,
      correlationId = readString(headers, "correlationId"),
      payload = payload
    )
  }

  private def readString(value: JsValue, field: String): Option[String] =
    (value \ field).asOpt[String]
}
