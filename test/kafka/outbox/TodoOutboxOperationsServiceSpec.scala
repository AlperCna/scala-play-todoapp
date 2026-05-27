package kafka.outbox

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec

import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class TodoOutboxOperationsServiceSpec extends PlaySpec with ScalaFutures {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  "TodoOutboxOperationsService" should {

    "summarize outbox counts for a tenant" in {
      val tenantId = UUID.randomUUID()
      val repository = new StubTodoOutboxRepository
      repository.countByStatusAndTenantValues = Map(
        (TodoOutboxStatus.Pending, tenantId) -> 2,
        (TodoOutboxStatus.Published, tenantId) -> 5,
        (TodoOutboxStatus.Failed, tenantId) -> 1
      )

      val service = new TodoOutboxOperationsService(repository)

      whenReady(service.summaryForTenant(tenantId)) { summary =>
        summary.pending mustBe 2
        summary.published mustBe 5
        summary.failed mustBe 1
        summary.total mustBe 8
      }
    }

    "return failed events page for a tenant" in {
      val tenantId = UUID.randomUUID()
      val failedEvent = sampleOutboxEvent(tenantId = tenantId, status = TodoOutboxStatus.Failed)
      val repository = new StubTodoOutboxRepository
      repository.countByStatusAndTenantValues = Map((TodoOutboxStatus.Failed, tenantId) -> 1)
      repository.failedByTenant = Seq(failedEvent)

      val service = new TodoOutboxOperationsService(repository)

      whenReady(service.failedEventsPageForTenant(tenantId, page = 1, pageSize = 10)) { result =>
        result.totalItems mustBe 1
        result.events.map(_.id) mustBe Seq(failedEvent.id.toString)
        result.events.head.lastError mustBe Some("simulated failure")
      }
    }

    "reset a failed event back to pending for replay" in {
      val tenantId = UUID.randomUUID()
      val failedEvent = sampleOutboxEvent(tenantId = tenantId, status = TodoOutboxStatus.Failed)
      val repository = new StubTodoOutboxRepository
      repository.eventByIdAndTenant = Some(failedEvent)

      val service = new TodoOutboxOperationsService(repository)

      whenReady(service.replayFailedEvent(tenantId, failedEvent.id)) { result =>
        result mustBe TodoOutboxReplayResult.Replayed
        repository.replayedIds must contain(failedEvent.id)
        repository.lastReplayAvailableAt.value.isBefore(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)) mustBe true
      }
    }

    "reject replay when the event is not failed" in {
      val tenantId = UUID.randomUUID()
      val publishedEvent = sampleOutboxEvent(tenantId = tenantId, status = TodoOutboxStatus.Published)
      val repository = new StubTodoOutboxRepository
      repository.eventByIdAndTenant = Some(publishedEvent)

      val service = new TodoOutboxOperationsService(repository)

      whenReady(service.replayFailedEvent(tenantId, publishedEvent.id)) { result =>
        result mustBe TodoOutboxReplayResult.NotFailed
        repository.replayedIds mustBe empty
      }
    }
  }

  private def sampleOutboxEvent(
    tenantId: UUID,
    status: String
  ): TodoOutboxEvent =
    TodoOutboxEvent(
      id = UUID.randomUUID(),
      aggregateType = "todo",
      aggregateId = UUID.randomUUID(),
      eventType = "TodoCreated",
      eventVersion = 1,
      tenantId = tenantId,
      userId = UUID.randomUUID(),
      payloadJson = """{"title":"Todo replay"}""",
      headersJson = """{"eventType":"TodoCreated"}""",
      status = status,
      attemptCount = 5,
      availableAt = LocalDateTime.now().minusMinutes(5),
      publishedAt = None,
      lastError = Some("simulated failure"),
      createdAt = LocalDateTime.now().minusHours(1)
    )

  private class StubTodoOutboxRepository extends TodoOutboxRepository {
    var countByStatusAndTenantValues: Map[(String, UUID), Int] = Map.empty
    var failedByTenant: Seq[TodoOutboxEvent] = Seq.empty
    var eventByIdAndTenant: Option[TodoOutboxEvent] = None
    var replayedIds: Seq[UUID] = Seq.empty
    var lastReplayAvailableAt: Option[LocalDateTime] = None

    override def create(outboxEvent: TodoOutboxEvent): Future[TodoOutboxEvent] =
      Future.successful(outboxEvent)

    override def findByAggregateId(aggregateId: UUID): Future[Seq[TodoOutboxEvent]] =
      Future.successful(Seq.empty)

    override def countByStatus(status: String): Future[Int] =
      Future.successful(0)

    override def countByStatusAndTenant(status: String, tenantId: UUID): Future[Int] =
      Future.successful(countByStatusAndTenantValues.getOrElse((status, tenantId), 0))

    override def findPublishable(limit: Int, availableBefore: LocalDateTime): Future[Seq[TodoOutboxEvent]] =
      Future.successful(Seq.empty)

    override def findFailedByTenantPaged(tenantId: UUID, page: Int, pageSize: Int): Future[Seq[TodoOutboxEvent]] =
      Future.successful(failedByTenant.filter(_.tenantId == tenantId))

    override def findById(id: UUID): Future[Option[TodoOutboxEvent]] =
      Future.successful(eventByIdAndTenant.filter(_.id == id))

    override def findByIdAndTenant(id: UUID, tenantId: UUID): Future[Option[TodoOutboxEvent]] =
      Future.successful(eventByIdAndTenant.filter(event => event.id == id && event.tenantId == tenantId))

    override def markPublished(id: UUID, publishedAt: LocalDateTime): Future[Boolean] =
      Future.successful(true)

    override def markFailure(
      id: UUID,
      nextAttemptCount: Int,
      nextAvailableAt: LocalDateTime,
      lastError: String,
      nextStatus: String
    ): Future[Boolean] = Future.successful(true)

    override def resetForReplay(id: UUID, nextAvailableAt: LocalDateTime): Future[Boolean] = {
      replayedIds = replayedIds :+ id
      lastReplayAvailableAt = Some(nextAvailableAt)
      Future.successful(true)
    }
  }
}
