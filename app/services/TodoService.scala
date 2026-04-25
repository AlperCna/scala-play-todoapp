package services

import dtos.{TodoCreateRequest, TodoResponse, TodoUpdateRequest}

import java.util.UUID
import scala.concurrent.Future

trait TodoService {
  def getTodos(userId: UUID): Future[Seq[TodoResponse]]

  def createTodo(userId: UUID, request: TodoCreateRequest): Future[TodoResponse]

  def updateTodo(userId: UUID, todoId: UUID, request: TodoUpdateRequest): Future[Option[TodoResponse]]

  def deleteTodo(userId: UUID, todoId: UUID): Future[Boolean]
}