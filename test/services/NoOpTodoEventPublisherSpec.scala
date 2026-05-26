package kafka.publisher

import kafka.events.DomainEventEnvelope
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json

import java.time.Instant
import java.util.UUID

class NoOpTodoEventPublisherSpec extends PlaySpec with ScalaFutures {

  "NoOpTodoEventPublisher" should {

    "complete successfully without changing the caller flow" in {
      val publisher = new NoOpTodoEventPublisher()
      val event = DomainEventEnvelope(
        eventId = UUID.randomUUID(),
        eventType = "TodoCreated",
        eventVersion = 1,
        occurredAt = Instant.now(),
        tenantId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        entityType = "todo",
        entityId = UUID.randomUUID(),
        correlationId = Some("req-1"),
        payload = Json.obj("todoId" -> UUID.randomUUID().toString)
      )

      whenReady(publisher.publish(event)) { result =>
        result mustBe ()
      }
    }
  }
}
