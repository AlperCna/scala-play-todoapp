package repositories

import models.AuditLog

import java.util.UUID
import scala.concurrent.Future

trait AuditLogRepository {
  def create(auditLog: AuditLog): Future[AuditLog]

  def findByUserId(userId: UUID): Future[Seq[AuditLog]]

  def findRecent(limit: Int): Future[Seq[AuditLog]]
}