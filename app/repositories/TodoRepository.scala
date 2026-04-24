package repositories

import models.Todo

import scala.concurrent.Future

trait TodoRepository {
  def findByUserId(userId: Long): Future[Seq[Todo]]

  def findByIdAndUserId(id: Long, userId: Long): Future[Option[Todo]]

  def create(todo: Todo): Future[Todo]

  def update(todo: Todo): Future[Todo]

  def delete(id: Long, userId: Long): Future[Boolean]
}