package services

import dtos.{AdminDashboardResponse, AdminTodoPageResponse, AdminTodoResponse, AdminUserResponse, UserPageResponse}
import models.{Todo, User}
import repositories.{TodoRepository, UserRepository}

import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AdminServiceImpl @Inject()(
                                  userRepository: UserRepository,
                                  todoRepository: TodoRepository
                                )(implicit ec: ExecutionContext) extends AdminService {

  private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  private def toAdminUserResponse(user: User): AdminUserResponse = {
    AdminUserResponse(
      id = user.id.toString,
      username = user.username,
      email = user.email,
      role = user.role,
      isActive = user.isActive,
      createdAt = user.createdAt.format(dateFormatter)
    )
  }

  private def toAdminTodoResponse(
                                   todo: Todo,
                                   username: String,
                                   email: String
                                 ): AdminTodoResponse = {
    AdminTodoResponse(
      id = todo.id.toString,
      userId = todo.userId.toString,
      username = username,
      email = email,
      title = todo.title,
      description = todo.description,
      isCompleted = todo.isCompleted,
      createdAt = todo.createdAt.format(dateFormatter)
    )
  }

  override def getDashboardStats(): Future[AdminDashboardResponse] = {
    for {
      totalUsers <- userRepository.countAll("")
      activeUsers <- userRepository.countActiveUsers()
      passiveUsers <- userRepository.countPassiveUsers()
      totalTodos <- todoRepository.countAllTodos()
    } yield {
      AdminDashboardResponse(
        totalUsers = totalUsers,
        activeUsers = activeUsers,
        passiveUsers = passiveUsers,
        totalTodos = totalTodos
      )
    }
  }

  override def getUsersPaged(
                              search: String,
                              page: Int,
                              pageSize: Int
                            ): Future[UserPageResponse] = {
    val normalizedSearch = search.trim
    val safePage = if (page < 1) 1 else page
    val safePageSize = if (pageSize < 1) 10 else pageSize

    for {
      totalItems <- userRepository.countAll(normalizedSearch)

      users <- userRepository.findAllPaged(
        normalizedSearch,
        safePage,
        safePageSize
      )
    } yield {
      val totalPages =
        if (totalItems == 0) 1
        else Math.ceil(totalItems.toDouble / safePageSize.toDouble).toInt

      UserPageResponse(
        users = users.map(toAdminUserResponse),
        currentPage = safePage,
        pageSize = safePageSize,
        totalItems = totalItems,
        totalPages = totalPages,
        search = normalizedSearch
      )
    }
  }

  override def getTodosPaged(
                              status: String,
                              search: String,
                              page: Int,
                              pageSize: Int
                            ): Future[AdminTodoPageResponse] = {
    val normalizedStatus =
      if (Set("all", "active", "completed").contains(status)) status else "all"

    val normalizedSearch = search.trim
    val safePage = if (page < 1) 1 else page
    val safePageSize = if (pageSize < 1) 10 else pageSize

    for {
      totalItems <- todoRepository.countAllTodosWithFilters(
        normalizedStatus,
        normalizedSearch
      )

      todosWithUsers <- todoRepository.findAllTodosWithUserPaged(
        normalizedStatus,
        normalizedSearch,
        safePage,
        safePageSize
      )
    } yield {
      val totalPages =
        if (totalItems == 0) 1
        else Math.ceil(totalItems.toDouble / safePageSize.toDouble).toInt

      AdminTodoPageResponse(
        todos = todosWithUsers.map {
          case (todo, username, email) =>
            toAdminTodoResponse(todo, username, email)
        },
        currentPage = safePage,
        pageSize = safePageSize,
        totalItems = totalItems,
        totalPages = totalPages,
        search = normalizedSearch,
        status = normalizedStatus
      )
    }
  }

  override def enableUser(userId: UUID): Future[Boolean] = {
    userRepository.setActive(userId, isActive = true)
  }

  override def disableUser(userId: UUID): Future[Boolean] = {
    userRepository.setActive(userId, isActive = false)
  }
}