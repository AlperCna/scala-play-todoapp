package services

import dtos.{TodoCreateRequest, TodoResponse, TodoUpdateRequest}
import models.Todo
import repositories.TodoRepository

import java.time.LocalDateTime
import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TodoServiceImpl @Inject()(
                                 todoRepository: TodoRepository
                               )(implicit ec: ExecutionContext) extends TodoService {

  private def toResponse(todo: Todo): TodoResponse = {
    TodoResponse(
      id = todo.id,
      title = todo.title,
      isCompleted = todo.isCompleted
    )
  }

  override def getTodos(userId: UUID): Future[Seq[TodoResponse]] = {
    todoRepository.findByUserId(userId).map { todos =>
      todos.map(toResponse)
    }
  }

  override def getTodoForEdit(
                               userId: UUID,
                               todoId: UUID
                             ): Future[Option[TodoResponse]] = {
    todoRepository.findByIdAndUserId(todoId, userId).map {
      case Some(todo) => Some(toResponse(todo))
      case None       => None
    }
  }

  override def createTodo(
                           userId: UUID,
                           request: TodoCreateRequest
                         ): Future[TodoResponse] = {
    val todo = Todo(
      id = UUID.randomUUID(),
      userId = userId,
      title = request.title,
      description = request.description,
      isCompleted = false,
      createdAt = LocalDateTime.now(),
      updatedAt = None,
      deletedAt = None,
      isDeleted = false
    )

    todoRepository.create(todo).map(toResponse)
  }

  override def updateTodo(
                           userId: UUID,
                           todoId: UUID,
                           request: TodoUpdateRequest
                         ): Future[Option[TodoResponse]] = {
    todoRepository.findByIdAndUserId(todoId, userId).flatMap {
      case Some(existingTodo) =>
        val updatedTodo = existingTodo.copy(
          title = request.title,
          description = request.description,
          isCompleted = request.isCompleted,
          updatedAt = Some(LocalDateTime.now())
        )

        todoRepository.update(updatedTodo).map { savedTodo =>
          Some(toResponse(savedTodo))
        }

      case None =>
        Future.successful(None)
    }
  }

  override def deleteTodo(
                           userId: UUID,
                           todoId: UUID
                         ): Future[Boolean] = {
    todoRepository.delete(todoId, userId)
  }

  override def toggleTodo(
                           userId: UUID,
                           todoId: UUID
                         ): Future[Option[TodoResponse]] = {
    todoRepository.findByIdAndUserId(todoId, userId).flatMap {
      case Some(existingTodo) =>
        val updatedTodo = existingTodo.copy(
          isCompleted = !existingTodo.isCompleted,
          updatedAt = Some(LocalDateTime.now())
        )

        todoRepository.update(updatedTodo).map { savedTodo =>
          Some(toResponse(savedTodo))
        }

      case None =>
        Future.successful(None)
    }
  }
}