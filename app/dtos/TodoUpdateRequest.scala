package dtos

import java.time.LocalDateTime

case class TodoUpdateRequest(
                              title: String,
                              description: Option[String],
                              isCompleted: Boolean,
                              dueDate: Option[LocalDateTime]
                            )