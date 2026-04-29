package services

import play.api.mvc.RequestHeader

import java.util.UUID
import scala.concurrent.Future

trait AuditLogService {

  def log(
           userId: Option[UUID],
           tenantId: Option[UUID],
           action: String,
           request: RequestHeader
         ): Future[Unit]
}