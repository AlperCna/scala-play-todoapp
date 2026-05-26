package kafka.publisher

import kafka.events.DomainEventEnvelope
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import play.api.libs.json.{JsObject, Json}

import java.nio.charset.StandardCharsets
import javax.inject.{Inject, Singleton}
import scala.jdk.CollectionConverters._

@Singleton
class KafkaTodoEventRecordFactory @Inject()() {

  def buildRecord(
    event: DomainEventEnvelope,
    settings: KafkaProducerSettings
  ): ProducerRecord[String, String] = {
    val key = s"${event.tenantId}:${event.entityId}"
    val value = Json.stringify(toJson(event))

    val record = new ProducerRecord[String, String](settings.todoEventsTopic, key, value)
    headersFor(event).foreach(record.headers().add)
    record
  }

  private def toJson(event: DomainEventEnvelope): JsObject =
    Json.obj(
      "eventId" -> event.eventId.toString,
      "eventType" -> event.eventType,
      "eventVersion" -> event.eventVersion,
      "occurredAt" -> event.occurredAt.toString,
      "tenantId" -> event.tenantId.toString,
      "userId" -> event.userId.toString,
      "entityType" -> event.entityType,
      "entityId" -> event.entityId.toString,
      "correlationId" -> event.correlationId,
      "payload" -> event.payload
    )

  private def headersFor(event: DomainEventEnvelope): Seq[RecordHeader] =
    Seq(
      "eventType" -> event.eventType,
      "eventVersion" -> event.eventVersion.toString,
      "tenantId" -> event.tenantId.toString,
      "userId" -> event.userId.toString,
      "entityType" -> event.entityType
    ).map { case (key, value) =>
      new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8))
    } ++ event.correlationId.toSeq.map { correlationId =>
      new RecordHeader("correlationId", correlationId.getBytes(StandardCharsets.UTF_8))
    }
}
