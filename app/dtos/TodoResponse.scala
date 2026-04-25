package dtos

import java.util.UUID

case class TodoResponse(
                         id: UUID,
                         title: String,
                         isCompleted: Boolean
                       )