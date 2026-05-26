package kafka.outbox

object TodoOutboxStatus {
  val Pending   = "PENDING"
  val Published = "PUBLISHED"
  val Failed    = "FAILED"
}
