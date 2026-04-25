package repositories

import models.Todo

import java.util.UUID
import scala.concurrent.Future

trait TodoRepository {
  def findByUserId(userId: UUID): Future[Seq[Todo]]

  def findByIdAndUserId(id: UUID, userId: UUID): Future[Option[Todo]]

  def create(todo: Todo): Future[Todo]

  def update(todo: Todo): Future[Todo]

  def delete(id: UUID, userId: UUID): Future[Boolean]
}