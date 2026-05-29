package kafka.outbox

import java.time.LocalDateTime

case class TodoOutboxReplayFilters(
  eventType: Option[String],
  createdFrom: Option[LocalDateTime],
  createdTo: Option[LocalDateTime]
) {
  def normalizedEventType: Option[String] =
    eventType.map(_.trim).filter(_.nonEmpty)
}

object TodoOutboxReplayFilters {
  val empty: TodoOutboxReplayFilters =
    TodoOutboxReplayFilters(
      eventType = None,
      createdFrom = None,
      createdTo = None
    )
}
