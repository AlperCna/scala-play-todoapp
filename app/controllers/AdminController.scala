package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class AdminController @Inject()(cc: ControllerComponents)
  extends AbstractController(cc) {

  def dashboard = Action { implicit request =>
    Ok(views.html.admin(
      userCount = 12,
      todoCount = 48,
      activeUserCount = 10
    )(request, request.flash))
  }

  def users = Action { implicit request =>
    val users = Seq("alper", "admin", "enes")

    Ok(views.html.adminUsers(users)(request, request.flash))
  }

}