package services

import dtos._
import kafka.outbox.{TodoOutboxBulkReplayResult, TodoOutboxOperationsService, TodoOutboxReplayFilters, TodoOutboxReplayResult, TodoOutboxStatusSummary}
import models.{AuditLog, Todo, User}
import repositories.{AuditLogRepository, TodoRepository, UserRepository}

import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AdminServiceImpl @Inject()(
                                  userRepository: UserRepository,
                                  todoRepository: TodoRepository,
                                  auditLogRepository: AuditLogRepository,
                                  todoOutboxOperationsService: TodoOutboxOperationsService
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

  private def toAdminTodoResponse(todo: Todo, username: String, email: String): AdminTodoResponse = {
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

  private def toAuditLogResponse(log: AuditLog): AuditLogResponse = {
    AuditLogResponse(
      id = log.id.toString,
      userId = log.userId.map(_.toString),
      action = log.action,
      ipAddress = log.ipAddress,
      userAgent = log.userAgent,
      createdAt = log.createdAt.format(dateFormatter)
    )
  }

  override def getDashboardStats(tenantId: UUID): Future[AdminDashboardResponse] = {
    for {
      totalUsers <- userRepository.countAll(tenantId, "")
      activeUsers <- userRepository.countActiveUsers(tenantId)
      passiveUsers <- userRepository.countPassiveUsers(tenantId)
      totalTodos <- todoRepository.countAllTodos(tenantId)
    } yield {
      AdminDashboardResponse(
        totalUsers = totalUsers,
        activeUsers = activeUsers,
        passiveUsers = passiveUsers,
        totalTodos = totalTodos
      )
    }
  }

  override def getUsersPaged(tenantId: UUID, search: String, page: Int, pageSize: Int): Future[UserPageResponse] = {
    val normalizedSearch = search.trim
    val safePage = if (page < 1) 1 else page
    val safePageSize = if (pageSize < 1) 10 else pageSize

    for {
      totalItems <- userRepository.countAll(tenantId, normalizedSearch)
      users <- userRepository.findAllPaged(tenantId, normalizedSearch, safePage, safePageSize)
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
                              tenantId: UUID,
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
      totalItems <- todoRepository.countAllTodosWithFilters(tenantId, normalizedStatus, normalizedSearch)
      todosWithUsers <- todoRepository.findAllTodosWithUserPaged(
        tenantId,
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

  override def getAuditLogsPaged(tenantId: UUID, page: Int, pageSize: Int): Future[AuditLogPageResponse] = {
    val safePage = if (page < 1) 1 else page
    val safePageSize = if (pageSize < 1) 10 else pageSize

    for {
      totalItems <- auditLogRepository.countAll(tenantId)
      logs <- auditLogRepository.findPaged(tenantId, safePage, safePageSize)
    } yield {
      val totalPages =
        if (totalItems == 0) 1
        else Math.ceil(totalItems.toDouble / safePageSize.toDouble).toInt

      AuditLogPageResponse(
        logs = logs.map(toAuditLogResponse),
        currentPage = safePage,
        pageSize = safePageSize,
        totalItems = totalItems,
        totalPages = totalPages
      )
    }
  }

  override def enableUser(userId: UUID): Future[Boolean] = {
    userRepository.setActive(userId, isActive = true)
  }

  override def disableUser(userId: UUID): Future[Boolean] = {
    userRepository.setActive(userId, isActive = false)
  }

  override def getOutboxSummary(tenantId: UUID): Future[TodoOutboxStatusSummary] =
    todoOutboxOperationsService.summaryForTenant(tenantId)

  override def getFailedOutboxEvents(
    tenantId: UUID,
    page: Int,
    pageSize: Int,
    filters: TodoOutboxReplayFilters
  ): Future[OutboxFailedEventPageResponse] =
    todoOutboxOperationsService.failedEventsPageForTenant(tenantId, page, pageSize, filters)

  override def replayFailedOutboxEvent(
    tenantId: UUID,
    requestedByUserId: UUID,
    outboxId: UUID
  ): Future[TodoOutboxReplayResult] =
    todoOutboxOperationsService.replayFailedEvent(tenantId, requestedByUserId, outboxId)

  override def replayFailedOutboxEvents(
    tenantId: UUID,
    requestedByUserId: UUID,
    filters: TodoOutboxReplayFilters
  ): Future[TodoOutboxBulkReplayResult] =
    todoOutboxOperationsService.replayFailedEventsInBulk(tenantId, requestedByUserId, filters)

  override def getOutboxReplayLogs(
    tenantId: UUID,
    page: Int,
    pageSize: Int
  ): Future[OutboxReplayLogPageResponse] =
    todoOutboxOperationsService.replayLogsPageForTenant(tenantId, page, pageSize)
}
