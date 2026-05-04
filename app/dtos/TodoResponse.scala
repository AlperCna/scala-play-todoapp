package dtos

import java.time.LocalDateTime
import java.util.UUID

case class TodoResponse(
                         id: UUID,
                         title: String,
                         description: Option[String],
                         isCompleted: Boolean,
                         dueDate: Option[LocalDateTime]
                       )