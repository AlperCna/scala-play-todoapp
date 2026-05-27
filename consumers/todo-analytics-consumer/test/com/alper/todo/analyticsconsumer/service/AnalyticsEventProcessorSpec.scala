package com.alper.todo.analyticsconsumer.service

import com.alper.todo.analyticsconsumer.config.{AnalyticsConsumerDatabaseSettings, AnalyticsConsumerSettings}
import com.alper.todo.analyticsconsumer.model.AnalyticsProcessingResult._
import com.alper.todo.analyticsconsumer.model.{AnalyticsCommand, TodoEventEnvelope}
import com.alper.todo.analyticsconsumer.ports.AnalyticsProjectionWriter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsNull, Json}

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class AnalyticsEventProcessorSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  "AnalyticsEventProcessor" should {
    "ignore unsupported event versions" in {
      val writer = new StubAnalyticsProjectionWriter
      val processor = new AnalyticsEventProcessor(sampleSettings, writer, new AnalyticsCommandFactory)

      whenReady(processor.process(sampleEnvelope(eventVersion = 2, eventType = "TodoCreated"))) { result =>
        result mustBe UnsupportedVersionIgnored
        writer.invocations mustBe 0
      }
    }

    "ignore unsupported event types" in {
      val writer = new StubAnalyticsProjectionWriter
      val processor = new AnalyticsEventProcessor(sampleSettings, writer, new AnalyticsCommandFactory)

      whenReady(processor.process(sampleEnvelope(eventType = "TodoArchived"))) { result =>
        result mustBe UnsupportedEventIgnored
        writer.invocations mustBe 0
      }
    }

    "write supported events to the projection writer" in {
      val writer = new StubAnalyticsProjectionWriter
      val processor = new AnalyticsEventProcessor(sampleSettings, writer, new AnalyticsCommandFactory)

      whenReady(processor.process(sampleEnvelope(eventType = "TodoCompleted"))) { result =>
        result mustBe Processed
        writer.invocations mustBe 1
        writer.lastCommand.map(_.eventType) mustBe Some("TodoCompleted")
        writer.lastCommand.map(_.title) mustBe Some("Analytics todo")
      }
    }
  }

  private val sampleSettings =
    AnalyticsConsumerSettings(
      bootstrapServers = "localhost:9092",
      topic = "todo.events.v1",
      groupId = "analytics-group",
      consumerName = "todo-analytics-consumer",
      supportedEventVersion = 1,
      database = AnalyticsConsumerDatabaseSettings(
        driver = "driver",
        url = "url",
        username = "user",
        password = "pass"
      )
    )

  private def sampleEnvelope(eventVersion: Int = 1, eventType: String): TodoEventEnvelope = {
    val todoId = UUID.randomUUID()
    TodoEventEnvelope(
      eventId = UUID.randomUUID(),
      eventType = eventType,
      eventVersion = eventVersion,
      occurredAt = Instant.parse("2026-05-27T12:00:00Z"),
      tenantId = UUID.randomUUID(),
      userId = UUID.randomUUID(),
      entityType = "todo",
      entityId = todoId,
      correlationId = None,
      payload = Json.obj(
        "todoId" -> todoId.toString,
        "title" -> "Analytics todo",
        "description" -> "Projection smoke",
        "isCompleted" -> (eventType == "TodoCompleted"),
        "dueDate" -> "2026-05-28T10:00:00",
        "createdAt" -> "2026-05-27T12:00:00",
        "updatedAt" -> "2026-05-27T12:30:00",
        "deletedAt" -> JsNull,
        "tenantId" -> UUID.randomUUID().toString,
        "userId" -> UUID.randomUUID().toString
      )
    )
  }

  private class StubAnalyticsProjectionWriter extends AnalyticsProjectionWriter {
    var invocations: Int = 0
    var lastCommand: Option[AnalyticsCommand] = None

    override def writeIfNew(command: AnalyticsCommand): Future[com.alper.todo.analyticsconsumer.model.AnalyticsProcessingResult] = {
      invocations += 1
      lastCommand = Some(command)
      Future.successful(Processed)
    }
  }
}
