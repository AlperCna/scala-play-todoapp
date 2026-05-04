package dtos

import java.time.LocalDateTime

case class TodoCreateRequest(
                              title: String,
                              description: Option[String],
                              dueDate: Option[LocalDateTime]
                            )