package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class AdminController @Inject()(cc: ControllerComponents)
  extends AbstractController(cc) {

  private def isLoggedIn(request: RequestHeader): Boolean = {
    request.session.get("userId").isDefined
  }

  private def isAdmin(request: RequestHeader): Boolean = {
    request.session.get("role").contains("ADMIN")
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

  def dashboard = Action { implicit request =>
    if (isAdmin(request)) {
      Ok(views.html.admin(
        userCount = 12,
        todoCount = 48,
        activeUserCount = 10
      ))
    } else {
      unauthorizedResult(request)
    }
  }

  def users = Action { implicit request =>
    if (isAdmin(request)) {
      val users = Seq("alper", "admin", "enes")
      Ok(views.html.adminUsers(users))
    } else {
      unauthorizedResult(request)
    }
  }
}