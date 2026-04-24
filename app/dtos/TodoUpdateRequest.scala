package dtos

case class TodoUpdateRequest(
                              title: String,
                              isCompleted: Boolean
                            )