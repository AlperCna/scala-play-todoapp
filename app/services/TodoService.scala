package services

import dtos.{TodoCreateRequest, TodoPageResponse, TodoResponse, TodoUpdateRequest}

import java.util.UUID
import scala.concurrent.Future

trait TodoService {

  def getTodos(userId: UUID, status: String, search: String): Future[Seq[TodoResponse]]

  def getTodosPaged(
                     userId: UUID,
                     status: String,
                     search: String,
                     page: Int,
                     pageSize: Int
                   ): Future[TodoPageResponse]

  def getTodoForEdit(userId: UUID, todoId: UUID): Future[Option[TodoResponse]]

  def createTodo(userId: UUID, tenantId: UUID, request: TodoCreateRequest): Future[TodoResponse]

  def updateTodo(userId: UUID, todoId: UUID, request: TodoUpdateRequest): Future[Option[TodoResponse]]

  def deleteTodo(userId: UUID, todoId: UUID): Future[Boolean]

  def toggleTodo(userId: UUID, todoId: UUID): Future[Option[TodoResponse]]
}