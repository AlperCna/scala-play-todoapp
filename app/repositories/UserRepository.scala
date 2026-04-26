package repositories

import models.User

import java.util.UUID
import scala.concurrent.Future

trait UserRepository {
  def findByEmail(email: String): Future[Option[User]]

  def findById(id: UUID): Future[Option[User]]

  def create(user: User): Future[User]

  def emailExists(email: String): Future[Boolean]
}