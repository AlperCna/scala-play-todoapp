package kafka.outbox

import dtos.{OutboxFailedEventPageResponse, OutboxFailedEventResponse}

import java.time.{LocalDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TodoOutboxOperationsService @Inject()(
  outboxRepository: TodoOutboxRepository
)(implicit ec: ExecutionContext) {

  private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def summaryForTenant(tenantId: UUID): Future[TodoOutboxStatusSummary] =
    for {
      pending <- outboxRepository.countByStatusAndTenant(TodoOutboxStatus.Pending, tenantId)
      published <- outboxRepository.countByStatusAndTenant(TodoOutboxStatus.Published, tenantId)
      failed <- outboxRepository.countByStatusAndTenant(TodoOutboxStatus.Failed, tenantId)
    } yield TodoOutboxStatusSummary(
      pending = pending,
      published = published,
      failed = failed
    )

  def failedEventsPageForTenant(
    tenantId: UUID,
    page: Int,
    pageSize: Int
  ): Future[OutboxFailedEventPageResponse] = {
    val safePage = if (page < 1) 1 else page
    val safePageSize = if (pageSize < 1) 10 else pageSize

    for {
      totalItems <- outboxRepository.countByStatusAndTenant(TodoOutboxStatus.Failed, tenantId)
      events <- outboxRepository.findFailedByTenantPaged(tenantId, safePage, safePageSize)
    } yield {
      val totalPages =
        if (totalItems == 0) 1
        else Math.ceil(totalItems.toDouble / safePageSize.toDouble).toInt

      OutboxFailedEventPageResponse(
        events = events.map(toFailedEventResponse),
        currentPage = safePage,
        pageSize = safePageSize,
        totalItems = totalItems,
        totalPages = totalPages
      )
    }
  }

  def replayFailedEvent(
    tenantId: UUID,
    outboxId: UUID
  ): Future[TodoOutboxReplayResult] =
    outboxRepository.findByIdAndTenant(outboxId, tenantId).flatMap {
      case None =>
        Future.successful(TodoOutboxReplayResult.NotFound)

      case Some(event) if event.status != TodoOutboxStatus.Failed =>
        Future.successful(TodoOutboxReplayResult.NotFailed)

      case Some(_) =>
        outboxRepository
          .resetForReplay(outboxId, LocalDateTime.now(ZoneOffset.UTC))
          .map(_ => TodoOutboxReplayResult.Replayed)
    }

  private def toFailedEventResponse(event: TodoOutboxEvent): OutboxFailedEventResponse =
    OutboxFailedEventResponse(
      id = event.id.toString,
      aggregateId = event.aggregateId.toString,
      eventType = event.eventType,
      eventVersion = event.eventVersion,
      attemptCount = event.attemptCount,
      status = event.status,
      lastError = event.lastError,
      availableAt = event.availableAt.format(dateFormatter),
      createdAt = event.createdAt.format(dateFormatter)
    )
}
