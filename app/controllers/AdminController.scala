package controllers

import security.{CustomProfile, SecureAction}
import services.AdminService

import javax.inject._
import play.api.mvc._

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
}
