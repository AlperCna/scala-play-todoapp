package kafka.outbox

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TodoOutboxMonitoringService @Inject()(
  outboxRepository: TodoOutboxRepository
)(implicit ec: ExecutionContext) {

  def currentSummary(): Future[TodoOutboxStatusSummary] =
    for {
      pending <- outboxRepository.countByStatus(TodoOutboxStatus.Pending)
      published <- outboxRepository.countByStatus(TodoOutboxStatus.Published)
      failed <- outboxRepository.countByStatus(TodoOutboxStatus.Failed)
    } yield TodoOutboxStatusSummary(
      pending = pending,
      published = published,
      failed = failed
    )
}
