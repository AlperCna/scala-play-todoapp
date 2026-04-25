package controllers

import dtos._
import forms._
import javax.inject._
import play.api.mvc._

@Singleton
class AuthController @Inject()(cc: ControllerComponents)
  extends AbstractController(cc) {

  def loginPage = Action { implicit request =>
    Ok(views.html.login(forms.LoginForm.form))
  }

  def registerPage = Action { implicit request =>
    Ok(views.html.register(forms.RegisterForm.form))
  }

  def login = Action { implicit request =>
    LoginForm.form.bindFromRequest().fold(
      formWithErrors => {
        BadRequest(views.html.login(formWithErrors))
      },
      data => {
        val dto = LoginRequest(
          email = data.email,
          password = data.password
        )

        Redirect(routes.TodoController.index())
          .flashing("success" -> s"Login success for ${dto.email}")
      }
    )
  }

  def register = Action { implicit request =>
    RegisterForm.form.bindFromRequest().fold(
      formWithErrors => {
        BadRequest(views.html.register(formWithErrors))
      },
      data => {
        val dto = RegisterRequest(
          username = data.username,
          email = data.email,
          password = data.password
        )

        Redirect(routes.AuthController.loginPage())
          .flashing("success" -> "Register successful. Please login.")
      }
    )
  }

  def logout = Action {
    Ok("Logout will be implemented here")
  }
}