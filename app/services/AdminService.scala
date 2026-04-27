package services

import dtos.{AdminDashboardResponse, AdminTodoPageResponse, UserPageResponse}

import java.util.UUID
import scala.concurrent.Future

trait AdminService {

  def getDashboardStats(): Future[AdminDashboardResponse]

  def getUsersPaged(
                     search: String,
                     page: Int,
                     pageSize: Int
                   ): Future[UserPageResponse]

  def getTodosPaged(
                     status: String,
                     search: String,
                     page: Int,
                     pageSize: Int
                   ): Future[AdminTodoPageResponse]

  def enableUser(userId: UUID): Future[Boolean]

  def disableUser(userId: UUID): Future[Boolean]
}