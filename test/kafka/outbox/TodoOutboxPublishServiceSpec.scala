package kafka.outbox

import kafka.events.DomainEventEnvelope
import kafka.publisher.{KafkaProducerSettingsLoader, KafkaTodoEventPublisher}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.libs.json.Json

import java.time.{Instant, LocalDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class TodoOutboxPublishServiceSpec extends PlaySpec with ScalaFutures {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  "TodoOutboxPublishService" should {

    "publish pending outbox rows and mark them as published" in {
      val repository = new StubTodoOutboxRepository
      val publisher = new StubKafkaTodoEventPublisher()
      val event = pendingOutboxEvent()
      repository.publishable = Seq(event)

      val service = new TodoOutboxPublishService(
        repository,
        new TodoOutboxEnvelopeFactory(),
        publisher,
        new KafkaProducerSettingsLoader(enabledKafkaConfig),
        new TodoOutboxWorkerSettingsLoader(enabledKafkaConfig)
      )

      whenReady(service.publishPendingBatch()) { result =>
        result.processed mustBe 1
        result.published mustBe 1
        result.retried mustBe 0
        result.failed mustBe 0
        result.skipped mustBe false
        publisher.publishedEvents.map(_.eventType) mustBe Seq("TodoCreated")
        repository.publishedIds must contain(event.id)
      }
    }

    "leave failed rows in pending with incremented attempt count before max attempts" in {
      val repository = new StubTodoOutboxRepository
      val publisher = new StubKafkaTodoEventPublisher(shouldFail = true)
      val event = pendingOutboxEvent(attemptCount = 1)
      repository.publishable = Seq(event)

      val service = new TodoOutboxPublishService(
        repository,
        new TodoOutboxEnvelopeFactory(),
        publisher,
        new KafkaProducerSettingsLoader(enabledKafkaConfig),
        new TodoOutboxWorkerSettingsLoader(enabledKafkaConfig)
      )

      whenReady(service.publishPendingBatch()) { result =>
        result.processed mustBe 1
        result.published mustBe 0
        result.retried mustBe 1
        result.failed mustBe 0
        repository.failedUpdates.head._2 mustBe 2
        repository.failedUpdates.head._5 mustBe TodoOutboxStatus.Pending
      }
    }

    "mark rows as failed when max attempts is reached" in {
      val repository = new StubTodoOutboxRepository
      val publisher = new StubKafkaTodoEventPublisher(shouldFail = true)
      val event = pendingOutboxEvent(attemptCount = 4)
      repository.publishable = Seq(event)

      val service = new TodoOutboxPublishService(
        repository,
        new TodoOutboxEnvelopeFactory(),
        publisher,
        new KafkaProducerSettingsLoader(enabledKafkaConfig),
        new TodoOutboxWorkerSettingsLoader(enabledKafkaConfig)
      )

      whenReady(service.publishPendingBatch()) { result =>
        result.processed mustBe 1
        result.published mustBe 0
        result.retried mustBe 0
        result.failed mustBe 1
        repository.failedUpdates.head._2 mustBe 5
        repository.failedUpdates.head._5 mustBe TodoOutboxStatus.Failed
      }
    }

    "skip publishing entirely when kafka is disabled" in {
      val repository = new StubTodoOutboxRepository
      repository.publishable = Seq(pendingOutboxEvent())
      val publisher = new StubKafkaTodoEventPublisher()

      val service = new TodoOutboxPublishService(
        repository,
        new TodoOutboxEnvelopeFactory(),
        publisher,
        new KafkaProducerSettingsLoader(Configuration("kafka.enabled" -> false)),
        new TodoOutboxWorkerSettingsLoader(Configuration("kafka.enabled" -> false))
      )

      whenReady(service.publishPendingBatch()) { result =>
        result.processed mustBe 0
        result.published mustBe 0
        result.retried mustBe 0
        result.failed mustBe 0
        result.skipped mustBe true
        publisher.publishedEvents mustBe empty
        repository.publishedIds mustBe empty
      }
    }
  }

  private val enabledKafkaConfig = Configuration(
    "kafka.enabled" -> true,
    "kafka.outbox.pollIntervalSeconds" -> 15,
    "kafka.outbox.batchSize" -> 20,
    "kafka.outbox.maxAttempts" -> 5,
    "kafka.outbox.initialRetryDelaySeconds" -> 30
  )

  private def pendingOutboxEvent(attemptCount: Int = 0): TodoOutboxEvent =
    TodoOutboxEvent(
      id = UUID.randomUUID(),
      aggregateType = "todo",
      aggregateId = UUID.randomUUID(),
      eventType = "TodoCreated",
      eventVersion = 1,
      tenantId = UUID.randomUUID(),
      userId = UUID.randomUUID(),
      payloadJson = Json.stringify(Json.obj("title" -> "Todo from outbox")),
      headersJson = Json.stringify(Json.obj("eventType" -> "TodoCreated", "correlationId" -> "corr-1")),
      status = TodoOutboxStatus.Pending,
      attemptCount = attemptCount,
      availableAt = LocalDateTime.now().minusSeconds(5),
      publishedAt = None,
      lastError = None,
      createdAt = LocalDateTime.now().minusMinutes(1)
    )

  private class StubTodoOutboxRepository extends TodoOutboxRepository {
    var publishable: Seq[TodoOutboxEvent] = Seq.empty
    var publishedIds: Seq[UUID] = Seq.empty
    var failedUpdates: Seq[(UUID, Int, LocalDateTime, String, String)] = Seq.empty

    override def create(outboxEvent: TodoOutboxEvent): Future[TodoOutboxEvent] =
      Future.successful(outboxEvent)

    override def findByAggregateId(aggregateId: UUID): Future[Seq[TodoOutboxEvent]] =
      Future.successful(publishable.filter(_.aggregateId == aggregateId))

    override def countByStatus(status: String): Future[Int] =
      Future.successful(publishable.count(_.status == status))

    override def countByStatusAndTenant(status: String, tenantId: UUID): Future[Int] =
      Future.successful(publishable.count(event => event.status == status && event.tenantId == tenantId))

    override def findPublishable(limit: Int, availableBefore: LocalDateTime): Future[Seq[TodoOutboxEvent]] =
      Future.successful(publishable.take(limit))

    override def findFailedByTenantPaged(tenantId: UUID, page: Int, pageSize: Int): Future[Seq[TodoOutboxEvent]] =
      Future.successful(publishable.filter(event => event.status == TodoOutboxStatus.Failed && event.tenantId == tenantId))

    override def findById(id: UUID): Future[Option[TodoOutboxEvent]] =
      Future.successful(publishable.find(_.id == id))

    override def findByIdAndTenant(id: UUID, tenantId: UUID): Future[Option[TodoOutboxEvent]] =
      Future.successful(publishable.find(event => event.id == id && event.tenantId == tenantId))

    override def markPublished(id: UUID, publishedAt: LocalDateTime): Future[Boolean] = {
      publishedIds = publishedIds :+ id
      Future.successful(true)
    }

    override def markFailure(
      id: UUID,
      nextAttemptCount: Int,
      nextAvailableAt: LocalDateTime,
      lastError: String,
      nextStatus: String
    ): Future[Boolean] = {
      failedUpdates = failedUpdates :+ (id, nextAttemptCount, nextAvailableAt, lastError, nextStatus)
      Future.successful(true)
    }

    override def resetForReplay(id: UUID, nextAvailableAt: LocalDateTime): Future[Boolean] =
      Future.successful(true)
  }

  private class StubKafkaTodoEventPublisher(shouldFail: Boolean = false) extends KafkaTodoEventPublisher(
        new KafkaProducerSettingsLoader(Configuration("kafka.enabled" -> true)),
        new kafka.publisher.KafkaTodoEventRecordFactory(),
        new kafka.publisher.KafkaProducerClient {
          override def send(record: org.apache.kafka.clients.producer.ProducerRecord[String, String]): Future[Unit] =
            if (shouldFail) Future.failed(new RuntimeException("simulated publish failure"))
            else Future.successful(())
        }
      ) {
    var publishedEvents: Seq[DomainEventEnvelope] = Seq.empty

    override def publish(event: DomainEventEnvelope): Future[Unit] = {
      publishedEvents = publishedEvents :+ event
      if (shouldFail) Future.failed(new RuntimeException("simulated publish failure"))
      else Future.successful(())
    }
  }
}
