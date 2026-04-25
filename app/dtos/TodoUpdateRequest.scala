package dtos

case class TodoUpdateRequest(
                              title: String,
                              description: Option[String],
                              isCompleted: Boolean
                            )