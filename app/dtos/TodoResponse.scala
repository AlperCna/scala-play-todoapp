package dtos

case class TodoResponse(
                         id: Long,
                         title: String,
                         isCompleted: Boolean
                       )