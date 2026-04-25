package controllers

import dtos._
import forms._
import javax.inject._
import play.api.mvc._

@Singleton
class AuthController @Inject()(cc: ControllerComponents)
  extends AbstractController(cc) {

  def loginPage = Action {
    Ok("Login page will be rendered here")
  }

  def registerPage = Action {
    Ok("Register page will be rendered here")
  }

  def login = Action { implicit request =>
    LoginForm.form.bindFromRequest().fold(
      formWithErrors => {
        BadRequest("Login form error")
      },
      data => {
        val dto = LoginRequest(
          email = data.email,
          password = data.password
        )

        Ok(s"Login success for ${dto.email}")
      }
    )
  }

  def register = Action { implicit request =>
    RegisterForm.form.bindFromRequest().fold(
      formWithErrors => {
        BadRequest("Register form error")
      },
      data => {
        val dto = RegisterRequest(
          username = data.username,
          email = data.email,
          password = data.password
        )

        Ok(s"Register success for ${dto.email}")
      }
    )
  }

  def logout = Action {
    Ok("Logout will be implemented here")
  }
}