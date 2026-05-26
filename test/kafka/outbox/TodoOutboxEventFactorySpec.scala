package kafka.outbox

import kafka.events.DomainEventEnvelope
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json

import java.time.Instant
import java.util.UUID

class TodoOutboxEventFactorySpec extends PlaySpec {

  private val factory = new TodoOutboxEventFactory()

  "TodoOutboxEventFactory" should {

    "convert a domain event into a pending outbox record" in {
      val envelope = DomainEventEnvelope(
        eventId = UUID.randomUUID(),
        eventType = "TodoCreated",
        eventVersion = 1,
        occurredAt = Instant.now(),
        tenantId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        entityType = "todo",
        entityId = UUID.randomUUID(),
        correlationId = Some("req-42"),
        payload = Json.obj("todoId" -> "123", "title" -> "Write docs")
      )

      val outbox = factory.fromEnvelope(envelope)

      outbox.aggregateType mustBe "todo"
      outbox.aggregateId mustBe envelope.entityId
      outbox.eventType mustBe envelope.eventType
      outbox.eventVersion mustBe envelope.eventVersion
      outbox.tenantId mustBe envelope.tenantId
      outbox.userId mustBe envelope.userId
      outbox.status mustBe TodoOutboxStatus.Pending
      outbox.attemptCount mustBe 0
      outbox.publishedAt mustBe None
      outbox.lastError mustBe None
      outbox.payloadJson must include("Write docs")
      outbox.headersJson must include("req-42")
    }
  }
}
