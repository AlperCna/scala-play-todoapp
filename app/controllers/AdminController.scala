package controllers

import dtos.OutboxBulkReplayResultResponse
import kafka.outbox.TodoOutboxReplayFilters
import kafka.outbox.TodoOutboxReplayResult
import security.{CustomProfile, SecureAction}
import services.AdminService

import javax.inject._
import play.api.libs.json.Json
import play.api.mvc._

import java.time.{LocalDate, LocalDateTime, LocalTime}
import scala.concurrent.ExecutionContext
import scala.util.Try

@Singleton
class AdminController @Inject()(
    cc: ControllerComponents,
    secure: SecureAction,
    adminService: AdminService
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  private val pageSize = 10

  def dashboard = secure.admin { profile => implicit request =>
    implicit val profileOpt: Option[CustomProfile] = Some(profile)
    adminService.getDashboardStats(profile.getTenantId).map { stats =>
      Ok(views.html.admin(stats))
    }
  }

  def users = secure.admin { profile => implicit request =>
    implicit val profileOpt: Option[CustomProfile] = Some(profile)
    val search = request.getQueryString("search").getOrElse("")
    val page   = parsePage(request)

    adminService.getUsersPaged(profile.getTenantId, search, page, pageSize).map { userPage =>
      Ok(views.html.adminUsers(userPage))
    }
  }

  def todos = secure.admin { profile => implicit request =>
    implicit val profileOpt: Option[CustomProfile] = Some(profile)
    val status = request.getQueryString("status").getOrElse("all")
    val search = request.getQueryString("search").getOrElse("")
    val page   = parsePage(request)

    adminService.getTodosPaged(profile.getTenantId, status, search, page, pageSize).map { todoPage =>
      Ok(views.html.adminTodos(todoPage))
    }
  }

  def auditLogs = secure.admin { profile => implicit request =>
    implicit val profileOpt: Option[CustomProfile] = Some(profile)
    val page = parsePage(request)

    adminService.getAuditLogsPaged(profile.getTenantId, page, pageSize).map { logPage =>
      Ok(views.html.adminAuditLogs(logPage))
    }
  }

  def outboxSummary = secure.admin { profile => implicit request =>
    adminService.getOutboxSummary(profile.getTenantId).map { summary =>
      Ok(Json.obj(
        "pending" -> summary.pending,
        "published" -> summary.published,
        "failed" -> summary.failed,
        "total" -> summary.total
      ))
    }
  }

  def failedOutboxEvents = secure.admin { profile => implicit request =>
    val page = parsePage(request)
    val filters = parseOutboxReplayFilters(request)

    adminService.getFailedOutboxEvents(profile.getTenantId, page, pageSize, filters).map { result =>
      Ok(Json.obj(
        "events" -> result.events.map { event =>
          Json.obj(
            "id" -> event.id,
            "aggregateId" -> event.aggregateId,
            "eventType" -> event.eventType,
            "eventVersion" -> event.eventVersion,
            "attemptCount" -> event.attemptCount,
            "replayCount" -> event.replayCount,
            "status" -> event.status,
            "lastError" -> event.lastError,
            "availableAt" -> event.availableAt,
            "createdAt" -> event.createdAt,
            "lastReplayedAt" -> event.lastReplayedAt,
            "lastReplayedByUserId" -> event.lastReplayedByUserId
          )
        },
        "currentPage" -> result.currentPage,
        "pageSize" -> result.pageSize,
        "totalItems" -> result.totalItems,
        "totalPages" -> result.totalPages,
        "filters" -> Json.obj(
          "eventType" -> filters.normalizedEventType,
          "createdFrom" -> filters.createdFrom.map(_.toString),
          "createdTo" -> filters.createdTo.map(_.toString)
        )
      ))
    }
  }

  def replayFailedOutboxEvent(id: String) = secure.admin { profile => implicit request =>
    Try(java.util.UUID.fromString(id)).toOption match {
      case Some(outboxId) =>
        adminService.replayFailedOutboxEvent(profile.getTenantId, profile.getUserId, outboxId).map {
          case TodoOutboxReplayResult.Replayed =>
            Ok(Json.obj(
              "id" -> id,
              "replayed" -> true,
              "message" -> "Outbox event replay icin tekrar queue'ya alindi."
            ))

          case TodoOutboxReplayResult.NotFound =>
            NotFound(Json.obj(
              "id" -> id,
              "replayed" -> false,
              "message" -> "Outbox event bulunamadi."
            ))

          case TodoOutboxReplayResult.NotFailed =>
            BadRequest(Json.obj(
              "id" -> id,
              "replayed" -> false,
              "message" -> "Sadece FAILED durumundaki event replay edilebilir."
            ))
        }

      case None =>
        scala.concurrent.Future.successful(
          BadRequest(Json.obj(
            "id" -> id,
            "replayed" -> false,
            "message" -> "Gecersiz outbox id."
          ))
        )
    }
  }

  def replayFailedOutboxEvents = secure.admin { profile => implicit request =>
    val filters = parseOutboxReplayFilters(request)

    adminService.replayFailedOutboxEvents(profile.getTenantId, profile.getUserId, filters).map { result =>
      val response = OutboxBulkReplayResultResponse(
        replayedCount = result.replayedCount,
        matchedCount = result.matchedCount,
        limited = result.limited,
        limit = result.limit,
        eventType = filters.normalizedEventType,
        createdFrom = filters.createdFrom.map(_.toString),
        createdTo = filters.createdTo.map(_.toString),
        message =
          if (result.replayedCount == 0) "Replay icin uygun FAILED event bulunamadi."
          else s"${result.replayedCount} event tekrar queue'ya alindi."
      )

      Ok(Json.obj(
        "replayedCount" -> response.replayedCount,
        "matchedCount" -> response.matchedCount,
        "limited" -> response.limited,
        "limit" -> response.limit,
        "eventType" -> response.eventType,
        "createdFrom" -> response.createdFrom,
        "createdTo" -> response.createdTo,
        "message" -> response.message
      ))
    }
  }

  def outboxReplayLogs = secure.admin { profile => implicit request =>
    val page = parsePage(request)

    adminService.getOutboxReplayLogs(profile.getTenantId, page, pageSize).map { result =>
      Ok(Json.obj(
        "logs" -> result.logs.map { log =>
          Json.obj(
            "id" -> log.id,
            "outboxId" -> log.outboxId,
            "requestedByUserId" -> log.requestedByUserId,
            "eventType" -> log.eventType,
            "replayMode" -> log.replayMode,
            "filterSummary" -> log.filterSummary,
            "replayedAt" -> log.replayedAt,
            "createdAt" -> log.createdAt
          )
        },
        "currentPage" -> result.currentPage,
        "pageSize" -> result.pageSize,
        "totalItems" -> result.totalItems,
        "totalPages" -> result.totalPages
      ))
    }
  }

  def enableUser(id: String) = secure.admin { _ => implicit request =>
    import java.util.UUID
    Try(UUID.fromString(id)).toOption match {
      case Some(userId) =>
        adminService.enableUser(userId).map {
          case true  => Redirect(routes.AdminController.users()).flashing("success" -> "Kullanıcı aktif hale getirildi.")
          case false => Redirect(routes.AdminController.users()).flashing("error" -> "Kullanıcı bulunamadı.")
        }

      case None =>
        import scala.concurrent.Future
        Future.successful(Redirect(routes.AdminController.users()).flashing("error" -> "Geçersiz kullanıcı id."))
    }
  }

  def disableUser(id: String) = secure.admin { _ => implicit request =>
    import java.util.UUID
    Try(UUID.fromString(id)).toOption match {
      case Some(userId) =>
        adminService.disableUser(userId).map {
          case true  => Redirect(routes.AdminController.users()).flashing("success" -> "Kullanıcı pasif hale getirildi.")
          case false => Redirect(routes.AdminController.users()).flashing("error" -> "Kullanıcı bulunamadı.")
        }

      case None =>
        import scala.concurrent.Future
        Future.successful(Redirect(routes.AdminController.users()).flashing("error" -> "Geçersiz kullanıcı id."))
    }
  }

  private def parsePage(request: RequestHeader): Int =
    request.getQueryString("page")
      .flatMap(v => Try(v.toInt).toOption)
      .filter(_ > 0)
      .getOrElse(1)

  private def parseOutboxReplayFilters(request: RequestHeader): TodoOutboxReplayFilters =
    TodoOutboxReplayFilters(
      eventType = request.getQueryString("eventType"),
      createdFrom = request.getQueryString("createdFrom").flatMap(parseFlexibleDateTime),
      createdTo = request.getQueryString("createdTo").flatMap(parseFlexibleDateTimeEnd)
    )

  private def parseFlexibleDateTime(value: String): Option[LocalDateTime] =
    Try(LocalDateTime.parse(value)).toOption.orElse {
      Try(LocalDate.parse(value).atStartOfDay()).toOption
    }

  private def parseFlexibleDateTimeEnd(value: String): Option[LocalDateTime] =
    Try(LocalDateTime.parse(value)).toOption.orElse {
      Try(LocalDate.parse(value).atTime(LocalTime.MAX)).toOption
    }
}
