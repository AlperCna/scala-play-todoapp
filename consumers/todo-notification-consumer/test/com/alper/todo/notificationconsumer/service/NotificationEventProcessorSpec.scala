package com.alper.todo.notificationconsumer.service

import com.alper.todo.notificationconsumer.config.NotificationConsumerSettings
import com.alper.todo.notificationconsumer.model.NotificationDispatchMode.{Disabled, Sandbox}
import com.alper.todo.notificationconsumer.model.NotificationProcessingResult._
import com.alper.todo.notificationconsumer.model.{NotificationCommand, TodoEventEnvelope, TodoPayload}
import com.alper.todo.notificationconsumer.ports.{NotificationSender, ProcessedEventStore}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class NotificationEventProcessorSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  "NotificationEventProcessor" should {
    "process TodoCreated in sandbox mode and mark it as processed" in {
      val store = new InMemoryProcessedEventStore()
      val sender = new CapturingNotificationSender()
      val processor = new NotificationEventProcessor(
        settings = NotificationConsumerSettings(
          bootstrapServers = "localhost:9092",
          topic = "todo.events.v1",
          groupId = "todo-notification-consumer-v1",
          dispatchMode = Sandbox,
          supportedEventVersion = 1
        ),
        processedEventStore = store,
        notificationSender = sender,
        commandFactory = new NotificationCommandFactory()
      )

      val result = processor.process(sampleEnvelope("TodoCreated"))

      whenReady(result) { value =>
        value shouldBe Processed
        sender.sentCommands should have size 1
        sender.sentCommands.head.dispatchMode shouldBe Sandbox
      }
    }

    "ignore duplicate event ids" in {
      val envelope = sampleEnvelope("TodoCreated")
      val store = new InMemoryProcessedEventStore(initialIds = Set(envelope.eventId))
      val sender = new CapturingNotificationSender()
      val processor = new NotificationEventProcessor(
        NotificationConsumerSettings("localhost:9092", "todo.events.v1", "todo-notification-consumer-v1", Sandbox, 1),
        store,
        sender,
        new NotificationCommandFactory()
      )

      whenReady(processor.process(envelope)) { value =>
        value shouldBe DuplicateIgnored
        sender.sentCommands shouldBe empty
      }
    }

    "ignore unsupported event versions" in {
      val store = new InMemoryProcessedEventStore()
      val sender = new CapturingNotificationSender()
      val processor = new NotificationEventProcessor(
        NotificationConsumerSettings("localhost:9092", "todo.events.v1", "todo-notification-consumer-v1", Sandbox, 1),
        store,
        sender,
        new NotificationCommandFactory()
      )

      whenReady(processor.process(sampleEnvelope("TodoCreated", eventVersion = 2))) { value =>
        value shouldBe UnsupportedVersionIgnored
        sender.sentCommands shouldBe empty
      }
    }

    "ignore unsupported event types" in {
      val store = new InMemoryProcessedEventStore()
      val sender = new CapturingNotificationSender()
      val processor = new NotificationEventProcessor(
        NotificationConsumerSettings("localhost:9092", "todo.events.v1", "todo-notification-consumer-v1", Sandbox, 1),
        store,
        sender,
        new NotificationCommandFactory()
      )

      whenReady(processor.process(sampleEnvelope("TodoDeleted"))) { value =>
        value shouldBe UnsupportedEventIgnored
        sender.sentCommands shouldBe empty
      }
    }

    "ignore all events when dispatch mode is disabled" in {
      val store = new InMemoryProcessedEventStore()
      val sender = new CapturingNotificationSender()
      val processor = new NotificationEventProcessor(
        NotificationConsumerSettings("localhost:9092", "todo.events.v1", "todo-notification-consumer-v1", Disabled, 1),
        store,
        sender,
        new NotificationCommandFactory()
      )

      whenReady(processor.process(sampleEnvelope("TodoCreated"))) { value =>
        value shouldBe DisabledIgnored
        sender.sentCommands shouldBe empty
      }
    }
  }

  private def sampleEnvelope(
    eventType: String,
    eventVersion: Int = 1
  ): TodoEventEnvelope = {
    val tenantId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    val userId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    val todoId = UUID.fromString("44444444-4444-4444-4444-444444444444")

    TodoEventEnvelope(
      eventId = UUID.randomUUID(),
      eventType = eventType,
      eventVersion = eventVersion,
      occurredAt = Instant.parse("2026-05-26T17:00:00Z"),
      tenantId = tenantId,
      userId = userId,
      entityType = "todo",
      entityId = todoId,
      correlationId = Some("spec-run"),
      payload = TodoPayload(
        todoId = todoId,
        title = "Finish Kafka workshop notes",
        description = Some("Prepare the notification flow explanation"),
        isCompleted = eventType == "TodoCompleted",
        dueDate = Some(LocalDateTime.parse("2026-05-27T09:30:00")),
        createdAt = LocalDateTime.parse("2026-05-26T19:30:00"),
        updatedAt = None,
        deletedAt = None,
        tenantId = tenantId,
        userId = userId
      )
    )
  }

  private class InMemoryProcessedEventStore(initialIds: Set[UUID] = Set.empty) extends ProcessedEventStore {
    private var processed: Set[UUID] = initialIds

    override def contains(eventId: UUID): Future[Boolean] =
      Future.successful(processed.contains(eventId))

    override def markProcessed(eventId: UUID): Future[Unit] = Future.successful {
      processed = processed + eventId
    }
  }

  private class CapturingNotificationSender extends NotificationSender {
    private var commands: Vector[NotificationCommand] = Vector.empty

    def sentCommands: Vector[NotificationCommand] = commands

    override def send(command: NotificationCommand): Future[Unit] = Future.successful {
      commands = commands :+ command
    }
  }
}
