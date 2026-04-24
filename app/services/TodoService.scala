package services

import dtos.{TodoCreateRequest, TodoResponse, TodoUpdateRequest}

import scala.concurrent.Future

trait TodoService {
  def getTodos(userId: Long): Future[Seq[TodoResponse]]

  def createTodo(userId: Long, request: TodoCreateRequest): Future[TodoResponse]

  def updateTodo(userId: Long, todoId: Long, request: TodoUpdateRequest): Future[Option[TodoResponse]]

  def deleteTodo(userId: Long, todoId: Long): Future[Boolean]
}