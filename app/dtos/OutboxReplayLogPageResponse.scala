package dtos

case class OutboxReplayLogPageResponse(
  logs: Seq[OutboxReplayLogResponse],
  currentPage: Int,
  pageSize: Int,
  totalItems: Int,
  totalPages: Int
)
