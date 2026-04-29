package controllers

import services.AdminService

import java.util.UUID
import javax.inject._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class AdminController @Inject()(
                                 cc: ControllerComponents,
                                 adminService: AdminService
                               )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  private val pageSize = 10

  private def isLoggedIn(request: RequestHeader): Boolean = {
    request.session.get("userId").isDefined
  }

  private def isAdmin(request: RequestHeader): Boolean = {
    request.session.get("role").contains("ADMIN")
  }

  private def getCurrentTenantId(request: RequestHeader): Option[UUID] = {
    request.session.get("tenantId").flatMap { id =>
      Try(UUID.fromString(id)).toOption
    }
  }

  private def unauthorizedResult(request: RequestHeader): Result = {
    if (!isLoggedIn(request)) {
      Redirect(routes.AuthController.loginPage())
        .flashing("error" -> "Lütfen önce giriş yapın.")
    } else {
      Redirect(routes.TodoController.index())
        .flashing("error" -> "Bu sayfaya erişim yetkiniz yok.")
    }
  }

  private def getSearch(request: RequestHeader): String = {
    request.getQueryString("search").getOrElse("")
  }

  private def getStatus(request: RequestHeader): String = {
    request.getQueryString("status").getOrElse("all")
  }

  private def getPage(request: RequestHeader): Int = {
    request.getQueryString("page")
      .flatMap(value => Try(value.toInt).toOption)
      .filter(_ > 0)
      .getOrElse(1)
  }

  def dashboard = Action.async { implicit request =>
    if (isAdmin(request)) {
      getCurrentTenantId(request) match {
        case Some(tenantId) =>
          adminService.getDashboardStats(tenantId).map { stats =>
            Ok(views.html.admin(stats))
          }
        case None =>
          Future.successful(unauthorizedResult(request))
      }
    } else {
      Future.successful(unauthorizedResult(request))
    }
  }

  def users = Action.async { implicit request =>
    if (isAdmin(request)) {
      getCurrentTenantId(request) match {
        case Some(tenantId) =>
          val search = getSearch(request)
          val page = getPage(request)

          adminService.getUsersPaged(tenantId, search, page, pageSize).map { userPage =>
            Ok(views.html.adminUsers(userPage))
          }
        case None =>
          Future.successful(unauthorizedResult(request))
      }
    } else {
      Future.successful(unauthorizedResult(request))
    }
  }

  def todos = Action.async { implicit request =>
    if (isAdmin(request)) {
      getCurrentTenantId(request) match {
        case Some(tenantId) =>
          val status = getStatus(request)
          val search = getSearch(request)
          val page = getPage(request)

          adminService.getTodosPaged(tenantId, status, search, page, pageSize).map { todoPage =>
            Ok(views.html.adminTodos(todoPage))
          }
        case None =>
          Future.successful(unauthorizedResult(request))
      }
    } else {
      Future.successful(unauthorizedResult(request))
    }
  }

  def auditLogs = Action.async { implicit request =>
    if (isAdmin(request)) {
      getCurrentTenantId(request) match {
        case Some(tenantId) =>
          val page = getPage(request)

          adminService.getAuditLogsPaged(tenantId, page, pageSize).map { logPage =>
            Ok(views.html.adminAuditLogs(logPage))
          }
        case None =>
          Future.successful(unauthorizedResult(request))
      }
    } else {
      Future.successful(unauthorizedResult(request))
    }
  }

  def enableUser(id: String) = Action.async { implicit request =>
    if (isAdmin(request)) {
      Try(UUID.fromString(id)).toOption match {
        case Some(userId) =>
          adminService.enableUser(userId).map {
            case true =>
              Redirect(routes.AdminController.users())
                .flashing("success" -> "Kullanıcı aktif hale getirildi.")

            case false =>
              Redirect(routes.AdminController.users())
                .flashing("error" -> "Kullanıcı bulunamadı.")
          }

        case None =>
          Future.successful(
            Redirect(routes.AdminController.users())
              .flashing("error" -> "Geçersiz kullanıcı id.")
          )
      }
    } else {
      Future.successful(unauthorizedResult(request))
    }
  }

  def disableUser(id: String) = Action.async { implicit request =>
    if (isAdmin(request)) {
      Try(UUID.fromString(id)).toOption match {
        case Some(userId) =>
          adminService.disableUser(userId).map {
            case true =>
              Redirect(routes.AdminController.users())
                .flashing("success" -> "Kullanıcı pasif hale getirildi.")

            case false =>
              Redirect(routes.AdminController.users())
                .flashing("error" -> "Kullanıcı bulunamadı.")
          }

        case None =>
          Future.successful(
            Redirect(routes.AdminController.users())
              .flashing("error" -> "Geçersiz kullanıcı id.")
          )
      }
    } else {
      Future.successful(unauthorizedResult(request))
    }
  }
}