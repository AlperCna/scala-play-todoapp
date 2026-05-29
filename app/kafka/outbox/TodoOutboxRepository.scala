package kafka.outbox

import java.util.UUID
import java.time.LocalDateTime
import scala.concurrent.Future

trait TodoOutboxRepository {
  def create(outboxEvent: TodoOutboxEvent): Future[TodoOutboxEvent]
  def findByAggregateId(aggregateId: UUID): Future[Seq[TodoOutboxEvent]]
  def countByStatus(status: String): Future[Int]
  def countByStatusAndTenant(status: String, tenantId: UUID): Future[Int]
  def countFailedByTenant(tenantId: UUID, filters: TodoOutboxReplayFilters): Future[Int]
  def findPublishable(limit: Int, availableBefore: LocalDateTime): Future[Seq[TodoOutboxEvent]]
  def findFailedByTenantPaged(
    tenantId: UUID,
    page: Int,
    pageSize: Int,
    filters: TodoOutboxReplayFilters
  ): Future[Seq[TodoOutboxEvent]]
  def findFailedByTenantForReplay(
    tenantId: UUID,
    filters: TodoOutboxReplayFilters,
    limit: Int
  ): Future[Seq[TodoOutboxEvent]]
  def findById(id: UUID): Future[Option[TodoOutboxEvent]]
  def findByIdAndTenant(id: UUID, tenantId: UUID): Future[Option[TodoOutboxEvent]]
  def markPublished(id: UUID, publishedAt: LocalDateTime): Future[Boolean]
  def markFailure(
    id: UUID,
    nextAttemptCount: Int,
    nextAvailableAt: LocalDateTime,
    lastError: String,
    nextStatus: String
  ): Future[Boolean]
  def replayFailedEvents(
    events: Seq[TodoOutboxEvent],
    replayedByUserId: UUID,
    replayedAt: LocalDateTime,
    replayMode: String,
    filterSummary: Option[String]
  ): Future[Int]
  def countReplayLogsByTenant(tenantId: UUID): Future[Int]
  def findReplayLogsByTenantPaged(
    tenantId: UUID,
    page: Int,
    pageSize: Int
  ): Future[Seq[TodoOutboxReplayLog]]
}
