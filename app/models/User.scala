package models

import java.time.LocalDateTime
import java.util.UUID

case class User(
                 id: UUID,
                 username: String,
                 email: String,
                 passwordHash: String,
                 role: String,
                 createdAt: LocalDateTime,
                 updatedAt: Option[LocalDateTime],
                 isActive: Boolean,
                 tenantId: UUID
               )