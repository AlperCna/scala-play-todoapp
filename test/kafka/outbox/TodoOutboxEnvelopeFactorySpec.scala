package kafka.outbox

import org.scalatestplus.play.PlaySpec

import java.time.LocalDateTime
import java.util.UUID

class TodoOutboxEnvelopeFactorySpec extends PlaySpec {

  "TodoOutboxEnvelopeFactory" should {

    "reconstruct a domain event envelope from an outbox row" in {
      val outboxEvent = TodoOutboxEvent(
        id = UUID.randomUUID(),
        aggregateType = "todo",
        aggregateId = UUID.randomUUID(),
        eventType = "TodoCompleted",
        eventVersion = 1,
        tenantId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        payloadJson = """{"todoId":"1","title":"Ship phase 4"}""",
        headersJson = """{"eventType":"TodoCompleted","correlationId":"corr-789"}""",
        status = TodoOutboxStatus.Pending,
        attemptCount = 0,
        availableAt = LocalDateTime.now(),
        publishedAt = None,
        lastError = None,
        createdAt = LocalDateTime.of(2026, 5, 26, 12, 0),
        replayCount = 0,
        lastReplayedAt = None,
        lastReplayedByUserId = None
      )

      val envelope = new TodoOutboxEnvelopeFactory().toEnvelope(outboxEvent)

      envelope.eventId mustBe outboxEvent.id
      envelope.eventType mustBe "TodoCompleted"
      envelope.entityType mustBe "todo"
      envelope.entityId mustBe outboxEvent.aggregateId
      envelope.correlationId mustBe Some("corr-789")
      (envelope.payload \ "title").as[String] mustBe "Ship phase 4"
    }
  }
}
