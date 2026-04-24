package repositories

import models.User

import scala.concurrent.Future

trait UserRepository {
  def findByEmail(email: String): Future[Option[User]]

  def findById(id: Long): Future[Option[User]]

  def create(user: User): Future[User]
}