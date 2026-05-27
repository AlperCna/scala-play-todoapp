package com.alper.todo.auditconsumer.service

import com.alper.todo.auditconsumer.config.{AuditConsumerDatabaseSettings, AuditConsumerSettings}
import com.alper.todo.auditconsumer.model.AuditConsumerRecordResult.{MalformedPayloadIgnored, Processed}
import com.alper.todo.auditconsumer.model.{AuditCommand, AuditProcessingResult}
import com.alper.todo.auditconsumer.ports.AuditLogWriter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class AuditKafkaRecordHandlerSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  "AuditKafkaRecordHandler" should {
    "process a valid TodoCreated payload" in {
      val processor = new AuditEventProcessor(
        settings = AuditConsumerSettings(
          bootstrapServers = "localhost:9092",
          topic = "todo.events.v1",
          groupId = "todo-audit-consumer-v1",
          consumerName = "todo-audit-consumer-v1",
          supportedEventVersion = 1,
          database = AuditConsumerDatabaseSettings("driver", "url", "sa", "pw")
        ),
        auditLogWriter = new NoOpAuditLogWriter(),
        commandFactory = new AuditCommandFactory()
      )

      val handler = new AuditKafkaRecordHandler(processor)

      val payload =
        """{"eventId":"a33cb46a-4915-4925-b7b3-d59c1e159db9","eventType":"TodoCreated","eventVersion":1,"occurredAt":"2026-05-26T19:19:14.392951Z","tenantId":"93416698-b138-418b-9caa-961676234ef4","userId":"a6c23121-f546-4fae-b503-8798747c9482","entityType":"todo","entityId":"370257c5-859d-4f55-9348-e20912426bab","correlationId":null,"payload":{"todoId":"370257c5-859d-4f55-9348-e20912426bab","title":"Kafka Full Create","description":"full e2e create","isCompleted":false,"dueDate":null,"createdAt":"2026-05-26T22:19:14.392951","updatedAt":null,"deletedAt":null,"tenantId":"93416698-b138-418b-9caa-961676234ef4","userId":"a6c23121-f546-4fae-b503-8798747c9482"}}"""

      whenReady(handler.handle(payload)) { result =>
        result shouldBe Processed
      }
    }

    "ignore malformed payloads without throwing" in {
      val processor = new AuditEventProcessor(
        settings = AuditConsumerSettings(
          bootstrapServers = "localhost:9092",
          topic = "todo.events.v1",
          groupId = "todo-audit-consumer-v1",
          consumerName = "todo-audit-consumer-v1",
          supportedEventVersion = 1,
          database = AuditConsumerDatabaseSettings("driver", "url", "sa", "pw")
        ),
        auditLogWriter = new NoOpAuditLogWriter(),
        commandFactory = new AuditCommandFactory()
      )

      val handler = new AuditKafkaRecordHandler(processor)

      whenReady(handler.handle("""{"broken":true}""")) { result =>
        result shouldBe MalformedPayloadIgnored
      }
    }
  }

  private class NoOpAuditLogWriter extends AuditLogWriter {
    override def writeIfNew(command: AuditCommand): Future[AuditProcessingResult] =
      Future.successful(AuditProcessingResult.Processed)
  }
}
