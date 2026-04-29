package services

import dtos.{AdminDashboardResponse, AdminTodoPageResponse, AuditLogPageResponse, UserPageResponse}

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
}