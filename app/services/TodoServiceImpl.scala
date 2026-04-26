package services

import dtos.{TodoCreateRequest, TodoResponse, TodoUpdateRequest, TodoPageResponse}
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
      description = todo.description,
      isCompleted = todo.isCompleted
    )
  }

  override def getTodos(userId: UUID, status: String, search: String): Future[Seq[TodoResponse]] = {
    val normalizedStatus =
      if (Set("all", "active", "completed").contains(status)) status else "all"

    val normalizedSearch = search.trim

    todoRepository
      .findByUserIdWithFilters(userId, normalizedStatus, normalizedSearch)
      .map { todos =>
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
  override def getTodosPaged(
                              userId: UUID,
                              status: String,
                              search: String,
                              page: Int,
                              pageSize: Int
                            ): Future[TodoPageResponse] = {

    val normalizedStatus =
      if (Set("all", "active", "completed").contains(status)) status else "all"

    val normalizedSearch = search.trim

    val safePage =
      if (page < 1) 1 else page

    val safePageSize =
      if (pageSize < 1) 5 else pageSize

    for {
      totalItems <- todoRepository.countByUserIdWithFilters(
        userId,
        normalizedStatus,
        normalizedSearch
      )

      todos <- todoRepository.findByUserIdWithFiltersPaged(
        userId,
        normalizedStatus,
        normalizedSearch,
        safePage,
        safePageSize
      )
    } yield {
      val totalPages =
        if (totalItems == 0) 1
        else Math.ceil(totalItems.toDouble / safePageSize.toDouble).toInt

      TodoPageResponse(
        todos = todos.map(toResponse),
        currentPage = safePage,
        pageSize = safePageSize,
        totalItems = totalItems,
        totalPages = totalPages,
        status = normalizedStatus,
        search = normalizedSearch
      )
    }
  }
}