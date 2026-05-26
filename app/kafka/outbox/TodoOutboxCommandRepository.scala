package kafka.outbox

import models.Todo

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.Future

trait TodoOutboxCommandRepository {
  def createTodoWithOutbox(todo: Todo, outboxEvent: TodoOutboxEvent): Future[Todo]
  def updateTodoWithOutbox(todo: Todo, outboxEvent: Option[TodoOutboxEvent]): Future[Todo]
  def deleteTodoWithOutbox(
    todoId: UUID,
    userId: UUID,
    deletedAt: LocalDateTime,
    outboxEvent: TodoOutboxEvent
  ): Future[Boolean]
}
