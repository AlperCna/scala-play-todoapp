package models

import java.time.LocalDateTime
import java.util.UUID

case class Tenant(
                   id: UUID,
                   name: String,
                   domain: String,
                   createdAt: LocalDateTime,
                   updatedAt: Option[LocalDateTime]
                 )
