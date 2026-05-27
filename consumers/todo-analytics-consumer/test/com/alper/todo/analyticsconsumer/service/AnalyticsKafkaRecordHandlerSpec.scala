package com.alper.todo.analyticsconsumer.service

import com.alper.todo.analyticsconsumer.config.{AnalyticsConsumerDatabaseSettings, AnalyticsConsumerSettings}
import com.alper.todo.analyticsconsumer.model.AnalyticsConsumerRecordResult.{MalformedPayloadIgnored, Processed}
import com.alper.todo.analyticsconsumer.model.AnalyticsProcessingResult
import com.alper.todo.analyticsconsumer.model.AnalyticsProcessingResult.DuplicateIgnored
import com.alper.todo.analyticsconsumer.model.{AnalyticsCommand, AnalyticsConsumerRecordResult, TodoEventEnvelope}
import com.alper.todo.analyticsconsumer.ports.AnalyticsProjectionWriter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsNull, Json}

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class AnalyticsKafkaRecordHandlerSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  "AnalyticsKafkaRecordHandler" should {
    "return malformed for invalid payload" in {
      val handler = new AnalyticsKafkaRecordHandler(processor(new StubAnalyticsProjectionWriter))

      whenReady(handler.handle("not-json")) { result =>
        result mustBe MalformedPayloadIgnored
      }
    }

    "return processed result for valid payload" in {
      val writer = new StubAnalyticsProjectionWriter
      writer.result = DuplicateIgnored
      val handler = new AnalyticsKafkaRecordHandler(processor(writer))

      whenReady(handler.handle(validJson)) { result =>
        result mustBe Processed(DuplicateIgnored)
      }
    }
  }

  private def processor(writer: StubAnalyticsProjectionWriter) =
    new AnalyticsEventProcessor(sampleSettings, writer, new AnalyticsCommandFactory)

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

  private val validJson: String = {
    val todoId = UUID.randomUUID()
    Json.stringify(
      Json.obj(
        "eventId" -> UUID.randomUUID().toString,
        "eventType" -> "TodoCreated",
        "eventVersion" -> 1,
        "occurredAt" -> "2026-05-27T12:00:00Z",
        "tenantId" -> UUID.randomUUID().toString,
        "userId" -> UUID.randomUUID().toString,
        "entityType" -> "todo",
        "entityId" -> todoId.toString,
        "correlationId" -> JsNull,
        "payload" -> Json.obj(
          "todoId" -> todoId.toString,
          "title" -> "Analytics todo",
          "description" -> "Projection smoke",
          "isCompleted" -> false,
          "dueDate" -> "2026-05-28T10:00:00",
          "createdAt" -> "2026-05-27T12:00:00",
          "updatedAt" -> "2026-05-27T12:30:00",
          "deletedAt" -> JsNull,
          "tenantId" -> UUID.randomUUID().toString,
          "userId" -> UUID.randomUUID().toString
        )
      )
    )
  }

  private class StubAnalyticsProjectionWriter extends AnalyticsProjectionWriter {
    var result: AnalyticsProcessingResult = AnalyticsProcessingResult.Processed

    override def writeIfNew(command: AnalyticsCommand): Future[AnalyticsProcessingResult] =
      Future.successful(result)
  }
}
