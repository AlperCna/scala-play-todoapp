package dtos

case class OutboxReplayResultResponse(
  id: String,
  replayed: Boolean,
  message: String
)
