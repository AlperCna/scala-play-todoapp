package models

import java.time.LocalDateTime
import java.util.UUID

case class Todo(
                 id: UUID,
                 userId: UUID,
                 title: String,
                 description: Option[String],
                 isCompleted: Boolean,
                 createdAt: LocalDateTime,
                 updatedAt: Option[LocalDateTime],
                 deletedAt: Option[LocalDateTime],
                 isDeleted: Boolean
               )