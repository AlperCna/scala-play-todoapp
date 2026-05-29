package com.alper.todo.auditconsumer.service

import com.alper.todo.auditconsumer.config.{AuditConsumerDatabaseSettings, AuditConsumerSettings}
import com.alper.todo.auditconsumer.model.AuditProcessingResult._
import com.alper.todo.auditconsumer.model.{AuditCommand, TodoEventEnvelope, TodoPayload}
import com.alper.todo.auditconsumer.ports.AuditLogWriter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDateTime}
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class AuditEventProcessorSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  "AuditEventProcessor" should {
    "write audit for supported events" in {
      val writer = new CapturingAuditLogWriter(Processed)
      val processor = new AuditEventProcessor(settings(1), writer, new AuditCommandFactory())

      whenReady(processor.process(sampleEnvelope("TodoCompleted"))) { result =>
        result shouldBe Processed
        writer.commands.head.action should include ("TODO_EVENT_COMPLETED")
      }
    }

    "ignore unsupported event versions" in {
      val writer = new CapturingAuditLogWriter(Processed)
      val processor = new AuditEventProcessor(settings(1), writer, new AuditCommandFactory())

      whenReady(processor.process(sampleEnvelope("TodoCreated", eventVersion = 2))) { result =>
        result shouldBe UnsupportedVersionIgnored
        writer.commands shouldBe empty
      }
    }

    "ignore unsupported event types" in {
      val writer = new CapturingAuditLogWriter(Processed)
      val processor = new AuditEventProcessor(settings(1), writer, new AuditCommandFactory())

      whenReady(processor.process(sampleEnvelope("TodoArchived"))) { result =>
        result shouldBe UnsupportedEventIgnored
        writer.commands shouldBe empty
      }
    }

    "surface duplicate results from the writer" in {
      val writer = new CapturingAuditLogWriter(DuplicateIgnored)
      val processor = new AuditEventProcessor(settings(1), writer, new AuditCommandFactory())

      whenReady(processor.process(sampleEnvelope("TodoDeleted"))) { result =>
        result shouldBe DuplicateIgnored
      }
    }
  }

  private def settings(version: Int) =
    AuditConsumerSettings(
      bootstrapServers = "localhost:9092",
      topic = "todo.events.v1",
      groupId = "todo-audit-consumer-v1",
      consumerName = "todo-audit-consumer-v1",
      dlqTopic = "todo.events.dlq.v1",
      supportedEventVersion = version,
      maxRetries = 3,
      retryBackoffMillis = 250L,
      database = AuditConsumerDatabaseSettings("driver", "url", "sa", "pw")
    )

  private def sampleEnvelope(eventType: String, eventVersion: Int = 1): TodoEventEnvelope = {
    val tenantId = UUID.randomUUID()
    val userId = UUID.randomUUID()
    TodoEventEnvelope(
      eventId = UUID.randomUUID(),
      eventType = eventType,
      eventVersion = eventVersion,
      occurredAt = Instant.parse("2026-05-27T10:00:00Z"),
      tenantId = tenantId,
      userId = userId,
      entityType = "todo",
      entityId = UUID.randomUUID(),
      correlationId = None,
      payload = TodoPayload(
        todoId = UUID.randomUUID(),
        title = "Audit Todo",
        description = Some("audit desc"),
        isCompleted = eventType == "TodoCompleted" || eventType == "TodoDeleted",
        dueDate = None,
        createdAt = LocalDateTime.parse("2026-05-27T13:00:00"),
        updatedAt = None,
        deletedAt = None,
        tenantId = tenantId,
        userId = userId
      )
    )
  }

  private class CapturingAuditLogWriter(result: com.alper.todo.auditconsumer.model.AuditProcessingResult)
      extends AuditLogWriter {
    var commands: Seq[AuditCommand] = Seq.empty

    override def writeIfNew(command: AuditCommand): Future[com.alper.todo.auditconsumer.model.AuditProcessingResult] = {
      commands = commands :+ command
      Future.successful(result)
    }
  }
}
