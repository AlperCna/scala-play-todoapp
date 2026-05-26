package kafka.publisher

import kafka.events.DomainEventEnvelope
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters._

class KafkaTodoEventRecordFactorySpec extends PlaySpec {

  private val settings = KafkaProducerSettings(
    enabled = true,
    bootstrapServers = "localhost:9092",
    clientId = "todo-play-app",
    todoEventsTopic = "todo.events.v1",
    acks = "all",
    enableIdempotence = true,
    requestTimeoutMs = 30000,
    deliveryTimeoutMs = 120000,
    maxInFlightRequestsPerConnection = 5
  )

  "KafkaTodoEventRecordFactory" should {

    "build a producer record with topic key payload and headers" in {
      val event = DomainEventEnvelope(
        eventId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        eventType = "TodoCreated",
        eventVersion = 1,
        occurredAt = Instant.parse("2026-05-26T09:00:00Z"),
        tenantId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
        userId = UUID.fromString("33333333-3333-3333-3333-333333333333"),
        entityType = "todo",
        entityId = UUID.fromString("44444444-4444-4444-4444-444444444444"),
        correlationId = Some("corr-123"),
        payload = Json.obj("title" -> "Write phase 3 docs")
      )

      val record = new KafkaTodoEventRecordFactory().buildRecord(event, settings)
      val headers = record.headers().asScala.map { header =>
        header.key() -> new String(header.value(), StandardCharsets.UTF_8)
      }.toMap

      record.topic() mustBe "todo.events.v1"
      record.key() mustBe "22222222-2222-2222-2222-222222222222:44444444-4444-4444-4444-444444444444"
      record.value() must include("TodoCreated")
      record.value() must include("Write phase 3 docs")
      headers("eventType") mustBe "TodoCreated"
      headers("eventVersion") mustBe "1"
      headers("tenantId") mustBe "22222222-2222-2222-2222-222222222222"
      headers("userId") mustBe "33333333-3333-3333-3333-333333333333"
      headers("entityType") mustBe "todo"
      headers("correlationId") mustBe "corr-123"
    }
  }
}
