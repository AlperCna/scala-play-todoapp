package dtos

case class AuditLogResponse(
                             id: String,
                             userId: Option[String],
                             action: String,
                             ipAddress: Option[String],
                             userAgent: Option[String],
                             createdAt: String
                           )

case class AuditLogPageResponse(
                                 logs: Seq[AuditLogResponse],
                                 currentPage: Int,
                                 pageSize: Int,
                                 totalItems: Int,
                                 totalPages: Int
                               )