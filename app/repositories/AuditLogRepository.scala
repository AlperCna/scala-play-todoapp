package repositories

import models.AuditLog

import java.util.UUID
import scala.concurrent.Future

trait AuditLogRepository {
  def create(auditLog: AuditLog): Future[AuditLog]

  def findByUserId(userId: UUID): Future[Seq[AuditLog]]

  def findRecent(tenantId: UUID, limit: Int): Future[Seq[AuditLog]]

  def findPaged(tenantId: UUID, page: Int, pageSize: Int): Future[Seq[AuditLog]]

  def countAll(tenantId: UUID): Future[Int]
}