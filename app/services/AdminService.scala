package services

import dtos.{AdminDashboardResponse, AdminTodoPageResponse, AuditLogPageResponse, OutboxFailedEventPageResponse, UserPageResponse}
import kafka.outbox.{TodoOutboxReplayResult, TodoOutboxStatusSummary}

import java.util.UUID
import scala.concurrent.Future

trait AdminService {

  def getDashboardStats(tenantId: UUID): Future[AdminDashboardResponse]

  def getUsersPaged(
                     tenantId: UUID,
                     search: String,
                     page: Int,
                     pageSize: Int
                   ): Future[UserPageResponse]

  def getTodosPaged(
                     tenantId: UUID,
                     status: String,
                     search: String,
                     page: Int,
                     pageSize: Int
                   ): Future[AdminTodoPageResponse]

  def getAuditLogsPaged(
                         tenantId: UUID,
                         page: Int,
                         pageSize: Int
                       ): Future[AuditLogPageResponse]

  def enableUser(userId: UUID): Future[Boolean]

  def disableUser(userId: UUID): Future[Boolean]

  def getOutboxSummary(tenantId: UUID): Future[TodoOutboxStatusSummary]

  def getFailedOutboxEvents(
    tenantId: UUID,
    page: Int,
    pageSize: Int
  ): Future[OutboxFailedEventPageResponse]

  def replayFailedOutboxEvent(
    tenantId: UUID,
    outboxId: UUID
  ): Future[TodoOutboxReplayResult]
}
