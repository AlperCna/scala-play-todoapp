package com.alper.todo.notificationconsumer.service

import com.alper.todo.notificationconsumer.config.NotificationConsumerSettings
import com.alper.todo.notificationconsumer.model.NotificationConsumerRecordResult.{MalformedPayloadIgnored, Processed}
import com.alper.todo.notificationconsumer.model.NotificationDispatchMode.Sandbox
import com.alper.todo.notificationconsumer.ports.{NotificationSender, ProcessedEventStore}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class NotificationKafkaRecordHandlerSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  "NotificationKafkaRecordHandler" should {
    "process a valid TodoCreated payload" in {
      val processor = new NotificationEventProcessor(
        settings = NotificationConsumerSettings(
          bootstrapServers = "localhost:9092",
          topic = "todo.events.v1",
          groupId = "todo-notification-consumer-v1",
          dispatchMode = Sandbox,
          supportedEventVersion = 1
        ),
        processedEventStore = new InMemoryProcessedEventStoreStub(),
        notificationSender = new NoOpNotificationSender(),
        commandFactory = new NotificationCommandFactory()
      )

      val handler = new NotificationKafkaRecordHandler(processor)

      val payload =
        """{"eventId":"a33cb46a-4915-4925-b7b3-d59c1e159db9","eventType":"TodoCreated","eventVersion":1,"occurredAt":"2026-05-26T19:19:14.392951Z","tenantId":"93416698-b138-418b-9caa-961676234ef4","userId":"a6c23121-f546-4fae-b503-8798747c9482","entityType":"todo","entityId":"370257c5-859d-4f55-9348-e20912426bab","correlationId":null,"payload":{"todoId":"370257c5-859d-4f55-9348-e20912426bab","title":"Kafka Full Create","description":"full e2e create","isCompleted":false,"dueDate":null,"createdAt":"2026-05-26T22:19:14.392951","updatedAt":null,"deletedAt":null,"tenantId":"93416698-b138-418b-9caa-961676234ef4","userId":"a6c23121-f546-4fae-b503-8798747c9482"}}"""

      whenReady(handler.handle(payload)) { result =>
        result shouldBe Processed
      }
    }

    "ignore malformed payloads without throwing" in {
      val processor = new NotificationEventProcessor(
        settings = NotificationConsumerSettings(
          bootstrapServers = "localhost:9092",
          topic = "todo.events.v1",
          groupId = "todo-notification-consumer-v1",
          dispatchMode = Sandbox,
          supportedEventVersion = 1
        ),
        processedEventStore = new InMemoryProcessedEventStoreStub(),
        notificationSender = new NoOpNotificationSender(),
        commandFactory = new NotificationCommandFactory()
      )

      val handler = new NotificationKafkaRecordHandler(processor)

      whenReady(handler.handle("""{"broken":true}""")) { result =>
        result shouldBe MalformedPayloadIgnored
      }
    }
  }

  private class InMemoryProcessedEventStoreStub extends ProcessedEventStore {
    private var ids = Set.empty[UUID]

    override def contains(eventId: UUID): Future[Boolean] =
      Future.successful(ids.contains(eventId))

    override def markProcessed(eventId: UUID): Future[Unit] = Future.successful {
      ids = ids + eventId
      ()
    }
  }

  private class NoOpNotificationSender extends NotificationSender {
    override def send(command: com.alper.todo.notificationconsumer.model.NotificationCommand): Future[Unit] =
      Future.successful(())
  }
}
