package kafka.outbox

import dtos.{OutboxReplayLogPageResponse, OutboxReplayLogResponse}
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

    "return failed events page for a tenant with replay metadata" in {
      val tenantId = UUID.randomUUID()
      val failedEvent = sampleOutboxEvent(
        tenantId = tenantId,
        status = TodoOutboxStatus.Failed,
        replayCount = 2,
        lastReplayedByUserId = Some(UUID.randomUUID())
      )
      val repository = new StubTodoOutboxRepository
      repository.failedCount = 1
      repository.failedByTenant = Seq(failedEvent)

      val service = new TodoOutboxOperationsService(repository)

      whenReady(service.failedEventsPageForTenant(tenantId, page = 1, pageSize = 10, TodoOutboxReplayFilters.empty)) { result =>
        result.totalItems mustBe 1
        result.events.map(_.id) mustBe Seq(failedEvent.id.toString)
        result.events.head.lastError mustBe Some("simulated failure")
        result.events.head.replayCount mustBe 2
        result.events.head.lastReplayedByUserId mustBe failedEvent.lastReplayedByUserId.map(_.toString)
      }
    }

    "reset a failed event back to pending for replay and log it" in {
      val tenantId = UUID.randomUUID()
      val requestedByUserId = UUID.randomUUID()
      val failedEvent = sampleOutboxEvent(tenantId = tenantId, status = TodoOutboxStatus.Failed)
      val repository = new StubTodoOutboxRepository
      repository.eventByIdAndTenant = Some(failedEvent)

      val service = new TodoOutboxOperationsService(repository)

      whenReady(service.replayFailedEvent(tenantId, requestedByUserId, failedEvent.id)) { result =>
        result mustBe TodoOutboxReplayResult.Replayed
        repository.replayedEvents.map(_.id) must contain(failedEvent.id)
        repository.lastReplayAvailableAt.value.isBefore(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)) mustBe true
        repository.lastReplayMode.value mustBe TodoOutboxReplayMode.Single
      }
    }

    "reject replay when the event is not failed" in {
      val tenantId = UUID.randomUUID()
      val requestedByUserId = UUID.randomUUID()
      val publishedEvent = sampleOutboxEvent(tenantId = tenantId, status = TodoOutboxStatus.Published)
      val repository = new StubTodoOutboxRepository
      repository.eventByIdAndTenant = Some(publishedEvent)

      val service = new TodoOutboxOperationsService(repository)

      whenReady(service.replayFailedEvent(tenantId, requestedByUserId, publishedEvent.id)) { result =>
        result mustBe TodoOutboxReplayResult.NotFailed
        repository.replayedEvents mustBe empty
      }
    }

    "bulk replay failed events with filters and limit information" in {
      val tenantId = UUID.randomUUID()
      val requestedByUserId = UUID.randomUUID()
      val matchingEvents = Seq(
        sampleOutboxEvent(tenantId = tenantId, status = TodoOutboxStatus.Failed, eventType = "TodoCreated"),
        sampleOutboxEvent(tenantId = tenantId, status = TodoOutboxStatus.Failed, eventType = "TodoCreated")
      )
      val repository = new StubTodoOutboxRepository
      repository.failedCount = 205
      repository.replayCandidates = matchingEvents

      val service = new TodoOutboxOperationsService(repository)
      val filters = TodoOutboxReplayFilters(
        eventType = Some("TodoCreated"),
        createdFrom = Some(LocalDateTime.of(2026, 5, 29, 10, 0)),
        createdTo = Some(LocalDateTime.of(2026, 5, 29, 12, 0))
      )

      whenReady(service.replayFailedEventsInBulk(tenantId, requestedByUserId, filters)) { result =>
        result.matchedCount mustBe 205
        result.replayedCount mustBe 2
        result.limited mustBe true
        result.limit mustBe 200
        repository.lastReplayMode.value mustBe TodoOutboxReplayMode.Bulk
        repository.lastReplayFilterSummary.value must include("eventType=TodoCreated")
      }
    }

    "return replay logs page for a tenant" in {
      val tenantId = UUID.randomUUID()
      val replayLog = TodoOutboxReplayLog(
        id = UUID.randomUUID(),
        outboxId = UUID.randomUUID(),
        tenantId = tenantId,
        requestedByUserId = UUID.randomUUID(),
        eventType = "TodoCompleted",
        replayMode = TodoOutboxReplayMode.Bulk,
        filterSummary = Some("eventType=TodoCompleted"),
        replayedAt = LocalDateTime.of(2026, 5, 29, 11, 30),
        createdAt = LocalDateTime.of(2026, 5, 29, 11, 30)
      )
      val repository = new StubTodoOutboxRepository
      repository.replayLogCount = 1
      repository.replayLogs = Seq(replayLog)

      val service = new TodoOutboxOperationsService(repository)

      whenReady(service.replayLogsPageForTenant(tenantId, page = 1, pageSize = 10)) { result =>
        result.totalItems mustBe 1
        result.logs.head.outboxId mustBe replayLog.outboxId.toString
        result.logs.head.replayMode mustBe TodoOutboxReplayMode.Bulk
      }
    }
  }

  private def sampleOutboxEvent(
    tenantId: UUID,
    status: String,
    eventType: String = "TodoCreated",
    replayCount: Int = 0,
    lastReplayedByUserId: Option[UUID] = None
  ): TodoOutboxEvent =
    TodoOutboxEvent(
      id = UUID.randomUUID(),
      aggregateType = "todo",
      aggregateId = UUID.randomUUID(),
      eventType = eventType,
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
      createdAt = LocalDateTime.now().minusHours(1),
      replayCount = replayCount,
      lastReplayedAt = if (replayCount > 0) Some(LocalDateTime.now().minusMinutes(15)) else None,
      lastReplayedByUserId = lastReplayedByUserId
    )

  private class StubTodoOutboxRepository extends TodoOutboxRepository {
    var countByStatusAndTenantValues: Map[(String, UUID), Int] = Map.empty
    var failedCount: Int = 0
    var failedByTenant: Seq[TodoOutboxEvent] = Seq.empty
    var replayCandidates: Seq[TodoOutboxEvent] = Seq.empty
    var eventByIdAndTenant: Option[TodoOutboxEvent] = None
    var replayedEvents: Seq[TodoOutboxEvent] = Seq.empty
    var lastReplayAvailableAt: Option[LocalDateTime] = None
    var lastReplayMode: Option[String] = None
    var lastReplayFilterSummary: Option[String] = None
    var replayLogCount: Int = 0
    var replayLogs: Seq[TodoOutboxReplayLog] = Seq.empty

    override def create(outboxEvent: TodoOutboxEvent): Future[TodoOutboxEvent] =
      Future.successful(outboxEvent)

    override def findByAggregateId(aggregateId: UUID): Future[Seq[TodoOutboxEvent]] =
      Future.successful(Seq.empty)

    override def countByStatus(status: String): Future[Int] =
      Future.successful(0)

    override def countByStatusAndTenant(status: String, tenantId: UUID): Future[Int] =
      Future.successful(countByStatusAndTenantValues.getOrElse((status, tenantId), 0))

    override def countFailedByTenant(tenantId: UUID, filters: TodoOutboxReplayFilters): Future[Int] =
      Future.successful(failedCount)

    override def findPublishable(limit: Int, availableBefore: LocalDateTime): Future[Seq[TodoOutboxEvent]] =
      Future.successful(Seq.empty)

    override def findFailedByTenantPaged(
      tenantId: UUID,
      page: Int,
      pageSize: Int,
      filters: TodoOutboxReplayFilters
    ): Future[Seq[TodoOutboxEvent]] =
      Future.successful(failedByTenant.filter(_.tenantId == tenantId))

    override def findFailedByTenantForReplay(
      tenantId: UUID,
      filters: TodoOutboxReplayFilters,
      limit: Int
    ): Future[Seq[TodoOutboxEvent]] =
      Future.successful(replayCandidates.take(limit))

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

    override def replayFailedEvents(
      events: Seq[TodoOutboxEvent],
      replayedByUserId: UUID,
      replayedAt: LocalDateTime,
      replayMode: String,
      filterSummary: Option[String]
    ): Future[Int] = {
      replayedEvents = events
      lastReplayAvailableAt = Some(replayedAt)
      lastReplayMode = Some(replayMode)
      lastReplayFilterSummary = filterSummary
      Future.successful(events.size)
    }

    override def countReplayLogsByTenant(tenantId: UUID): Future[Int] =
      Future.successful(replayLogCount)

    override def findReplayLogsByTenantPaged(
      tenantId: UUID,
      page: Int,
      pageSize: Int
    ): Future[Seq[TodoOutboxReplayLog]] =
      Future.successful(replayLogs.filter(_.tenantId == tenantId))
  }
}
