package repositories

import models.User

import java.util.UUID
import scala.concurrent.Future

trait UserRepository {
  def findByEmail(email: String): Future[Option[User]]

  def findById(id: UUID): Future[Option[User]]

  def create(user: User): Future[User]

  def emailExists(email: String): Future[Boolean]

  def countAll(search: String): Future[Int]

  def countActiveUsers(): Future[Int]

  def countPassiveUsers(): Future[Int]

  def findAllPaged(search: String, page: Int, pageSize: Int): Future[Seq[User]]

  def setActive(userId: UUID, isActive: Boolean): Future[Boolean]
}