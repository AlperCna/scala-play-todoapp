package kafka.outbox

import java.util.UUID
import scala.concurrent.Future

trait TodoOutboxRepository {
  def create(outboxEvent: TodoOutboxEvent): Future[TodoOutboxEvent]
  def findByAggregateId(aggregateId: UUID): Future[Seq[TodoOutboxEvent]]
  def countByStatus(status: String): Future[Int]
}
