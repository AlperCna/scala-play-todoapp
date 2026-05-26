package dtos

case class OutboxFailedEventPageResponse(
  events: Seq[OutboxFailedEventResponse],
  currentPage: Int,
  pageSize: Int,
  totalItems: Int,
  totalPages: Int
)
