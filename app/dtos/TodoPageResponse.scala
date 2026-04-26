package dtos

case class TodoPageResponse(
                             todos: Seq[TodoResponse],
                             currentPage: Int,
                             pageSize: Int,
                             totalItems: Int,
                             totalPages: Int,
                             status: String,
                             search: String
                           )