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

  private val maxBulkReplaySize = 200
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
    pageSize: Int,
    filters: TodoOutboxReplayFilters
  ): Future[OutboxFailedEventPageResponse] = {
    val safePage = if (page < 1) 1 else page
    val safePageSize = if (pageSize < 1) 10 else pageSize

    for {
      totalItems <- outboxRepository.countFailedByTenant(tenantId, filters)
      events <- outboxRepository.findFailedByTenantPaged(tenantId, safePage, safePageSize, filters)
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
    requestedByUserId: UUID,
    outboxId: UUID
  ): Future[TodoOutboxReplayResult] =
    outboxRepository.findByIdAndTenant(outboxId, tenantId).flatMap {
      case None =>
        Future.successful(TodoOutboxReplayResult.NotFound)

      case Some(event) if event.status != TodoOutboxStatus.Failed =>
        Future.successful(TodoOutboxReplayResult.NotFailed)

      case Some(event) =>
        val replayedAt = LocalDateTime.now(ZoneOffset.UTC)
        outboxRepository
          .replayFailedEvents(
            events = Seq(event),
            replayedByUserId = requestedByUserId,
            replayedAt = replayedAt,
            replayMode = TodoOutboxReplayMode.Single,
            filterSummary = Some(s"outboxId=${outboxId.toString}")
          )
          .map {
            case count if count > 0 => TodoOutboxReplayResult.Replayed
            case _                  => TodoOutboxReplayResult.NotFailed
          }
    }

  def replayFailedEventsInBulk(
    tenantId: UUID,
    requestedByUserId: UUID,
    filters: TodoOutboxReplayFilters
  ): Future[TodoOutboxBulkReplayResult] = {
    val replayedAt = LocalDateTime.now(ZoneOffset.UTC)

    for {
      totalMatching <- outboxRepository.countFailedByTenant(tenantId, filters)
      events <- outboxRepository.findFailedByTenantForReplay(tenantId, filters, maxBulkReplaySize)
      replayedCount <- outboxRepository.replayFailedEvents(
        events = events,
        replayedByUserId = requestedByUserId,
        replayedAt = replayedAt,
        replayMode = TodoOutboxReplayMode.Bulk,
        filterSummary = Some(filterSummary(filters))
      )
    } yield TodoOutboxBulkReplayResult(
      matchedCount = totalMatching,
      replayedCount = replayedCount,
      limited = totalMatching > maxBulkReplaySize,
      limit = maxBulkReplaySize
    )
  }

  def replayLogsPageForTenant(
    tenantId: UUID,
    page: Int,
    pageSize: Int
  ): Future[dtos.OutboxReplayLogPageResponse] = {
    val safePage = if (page < 1) 1 else page
    val safePageSize = if (pageSize < 1) 10 else pageSize

    for {
      totalItems <- outboxRepository.countReplayLogsByTenant(tenantId)
      logs <- outboxRepository.findReplayLogsByTenantPaged(tenantId, safePage, safePageSize)
    } yield {
      val totalPages =
        if (totalItems == 0) 1
        else Math.ceil(totalItems.toDouble / safePageSize.toDouble).toInt

      dtos.OutboxReplayLogPageResponse(
        logs = logs.map { log =>
          dtos.OutboxReplayLogResponse(
            id = log.id.toString,
            outboxId = log.outboxId.toString,
            requestedByUserId = log.requestedByUserId.toString,
            eventType = log.eventType,
            replayMode = log.replayMode,
            filterSummary = log.filterSummary,
            replayedAt = log.replayedAt.format(dateFormatter),
            createdAt = log.createdAt.format(dateFormatter)
          )
        },
        currentPage = safePage,
        pageSize = safePageSize,
        totalItems = totalItems,
        totalPages = totalPages
      )
    }
  }

  private def toFailedEventResponse(event: TodoOutboxEvent): OutboxFailedEventResponse =
    OutboxFailedEventResponse(
      id = event.id.toString,
      aggregateId = event.aggregateId.toString,
      eventType = event.eventType,
      eventVersion = event.eventVersion,
      attemptCount = event.attemptCount,
      replayCount = event.replayCount,
      status = event.status,
      lastError = event.lastError,
      availableAt = event.availableAt.format(dateFormatter),
      createdAt = event.createdAt.format(dateFormatter),
      lastReplayedAt = event.lastReplayedAt.map(_.format(dateFormatter)),
      lastReplayedByUserId = event.lastReplayedByUserId.map(_.toString)
    )

  private def filterSummary(filters: TodoOutboxReplayFilters): String = {
    val segments = Seq(
      filters.normalizedEventType.map(value => s"eventType=$value"),
      filters.createdFrom.map(value => s"createdFrom=${value.format(dateFormatter)}"),
      filters.createdTo.map(value => s"createdTo=${value.format(dateFormatter)}")
    ).flatten

    if (segments.isEmpty) "all failed events"
    else segments.mkString(", ")
  }
}
