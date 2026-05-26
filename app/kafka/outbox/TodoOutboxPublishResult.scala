package kafka.outbox

case class TodoOutboxPublishResult(
  processed: Int,
  published: Int,
  retried: Int,
  failed: Int,
  skipped: Boolean
)

object TodoOutboxPublishResult {
  val Skipped: TodoOutboxPublishResult =
    TodoOutboxPublishResult(
      processed = 0,
      published = 0,
      retried = 0,
      failed = 0,
      skipped = true
    )
}
