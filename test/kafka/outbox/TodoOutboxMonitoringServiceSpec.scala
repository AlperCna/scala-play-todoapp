package kafka.outbox

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class TodoOutboxMonitoringServiceSpec extends PlaySpec with ScalaFutures {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  "TodoOutboxMonitoringService" should {

    "summarize outbox counts by status" in {
      val repository = new TodoOutboxRepository {
        override def create(outboxEvent: TodoOutboxEvent): Future[TodoOutboxEvent] =
          Future.successful(outboxEvent)

        override def findByAggregateId(aggregateId: UUID): Future[Seq[TodoOutboxEvent]] =
          Future.successful(Seq.empty)

        override def countByStatus(status: String): Future[Int] =
          Future.successful(status match {
            case TodoOutboxStatus.Pending   => 3
            case TodoOutboxStatus.Published => 7
            case TodoOutboxStatus.Failed    => 2
            case _                          => 0
          })

        override def countByStatusAndTenant(status: String, tenantId: UUID): Future[Int] =
          Future.successful(0)

        override def countFailedByTenant(tenantId: UUID, filters: TodoOutboxReplayFilters): Future[Int] =
          Future.successful(0)

        override def findPublishable(limit: Int, availableBefore: LocalDateTime): Future[Seq[TodoOutboxEvent]] =
          Future.successful(Seq.empty)

        override def findFailedByTenantPaged(
          tenantId: UUID,
          page: Int,
          pageSize: Int,
          filters: TodoOutboxReplayFilters
        ): Future[Seq[TodoOutboxEvent]] =
          Future.successful(Seq.empty)

        override def findFailedByTenantForReplay(
          tenantId: UUID,
          filters: TodoOutboxReplayFilters,
          limit: Int
        ): Future[Seq[TodoOutboxEvent]] =
          Future.successful(Seq.empty)

        override def findById(id: UUID): Future[Option[TodoOutboxEvent]] =
          Future.successful(None)

        override def findByIdAndTenant(id: UUID, tenantId: UUID): Future[Option[TodoOutboxEvent]] =
          Future.successful(None)

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
        ): Future[Int] =
          Future.successful(events.size)

        override def countReplayLogsByTenant(tenantId: UUID): Future[Int] =
          Future.successful(0)

        override def findReplayLogsByTenantPaged(
          tenantId: UUID,
          page: Int,
          pageSize: Int
        ): Future[Seq[TodoOutboxReplayLog]] =
          Future.successful(Seq.empty)
      }

      whenReady(new TodoOutboxMonitoringService(repository).currentSummary()) { summary =>
        summary.pending mustBe 3
        summary.published mustBe 7
        summary.failed mustBe 2
        summary.total mustBe 12
      }
    }
  }
}
