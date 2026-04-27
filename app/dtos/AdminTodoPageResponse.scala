package dtos

case class AdminTodoResponse(
                              id: String,
                              userId: String,
                              username: String,
                              email: String,
                              title: String,
                              description: Option[String],
                              isCompleted: Boolean,
                              createdAt: String
                            )

case class AdminTodoPageResponse(
                                  todos: Seq[AdminTodoResponse],
                                  currentPage: Int,
                                  pageSize: Int,
                                  totalItems: Int,
                                  totalPages: Int,
                                  search: String,
                                  status: String
                                )