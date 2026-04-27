package services

import models.AuditLog
import repositories.AuditLogRepository
import play.api.mvc.RequestHeader

import java.time.LocalDateTime
import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuditLogServiceImpl @Inject()(
                                     auditLogRepository: AuditLogRepository
                                   )(implicit ec: ExecutionContext) extends AuditLogService {

  override def log(
                    userId: Option[UUID],
                    action: String,
                    request: RequestHeader
                  ): Future[Unit] = {

    val auditLog = AuditLog(
      id = UUID.randomUUID(),
      userId = userId,
      action = action,
      ipAddress = Some(request.remoteAddress),
      userAgent = request.headers.get("User-Agent"),
      createdAt = LocalDateTime.now()
    )

    auditLogRepository.create(auditLog).map(_ => ())
  }
}