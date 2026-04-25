package dtos

case class TodoCreateRequest(
                              title: String,
                              description: Option[String]
                            )