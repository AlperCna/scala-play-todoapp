package kafka.outbox

case class TodoOutboxStatusSummary(
  pending: Int,
  published: Int,
  failed: Int
) {
  val total: Int = pending + published + failed
}
