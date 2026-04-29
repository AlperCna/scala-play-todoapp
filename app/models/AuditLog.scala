package models

import java.time.LocalDateTime
import java.util.UUID

case class AuditLog(
                     id: UUID,
                     userId: Option[UUID],
                     action: String,
                     ipAddress: Option[String],
                     userAgent: Option[String],
                     createdAt: LocalDateTime,
                     tenantId: Option[UUID]
                   )