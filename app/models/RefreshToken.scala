package models

import java.time.LocalDateTime
import java.util.UUID

case class RefreshToken(
                         id: UUID,
                         userId: UUID,
                         tokenHash: String,
                         expiresAt: LocalDateTime,
                         createdAt: LocalDateTime,
                         revokedAt: Option[LocalDateTime],
                         isRevoked: Boolean
                       )