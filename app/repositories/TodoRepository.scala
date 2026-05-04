package repositories

import models.Todo

import java.util.UUID
import scala.concurrent.Future

trait TodoRepository {
  def findByUserId(userId: UUID): Future[Seq[Todo]]

  def findByUserIdWithStatus(userId: UUID, status: String): Future[Seq[Todo]]

  def findByIdAndUserId(id: UUID, userId: UUID): Future[Option[Todo]]

  def create(todo: Todo): Future[Todo]

  def update(todo: Todo): Future[Todo]

  def delete(id: UUID, userId: UUID): Future[Boolean]

  def findByUserIdWithFilters(userId: UUID, status: String, search: String): Future[Seq[Todo]]

  def findByUserIdWithFiltersPaged(
                                    userId: UUID,
                                    status: String,
                                    search: String,
                                    page: Int,
                                    pageSize: Int
                                  ): Future[Seq[Todo]]

  def countByUserIdWithFilters(
                                userId: UUID,
                                status: String,
                                search: String
                              ): Future[Int]


  def countAllTodos(tenantId: UUID): Future[Int]

  def countAllTodosWithFilters(tenantId: UUID, status: String, search: String): Future[Int]

  def findAllTodosWithUserPaged(
                                 tenantId: UUID,
                                 status: String,
                                 search: String,
                                 page: Int,
                                 pageSize: Int
                               ): Future[Seq[(Todo, String, String)]]

  // Due date'i yarın olan, tamamlanmamış todoları kullanıcı bilgisiyle döner
  // Dönen tuple: (Todo, userEmail, username)
  def findDueTomorrow(): Future[Seq[(Todo, String, String)]]
}