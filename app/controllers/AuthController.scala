package controllers

import dtos._
import forms._
import repositories.UserRepository
import services.AuthService

import javax.inject._
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthController @Inject()(
                                cc: ControllerComponents,
                                authService: AuthService,
                                userRepository: UserRepository
                              )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def loginPage = Action { implicit request =>
    Ok(views.html.login(LoginForm.form))
  }

  def registerPage = Action { implicit request =>
    Ok(views.html.register(RegisterForm.form))
  }

  def checkEmail = Action.async { implicit request =>
    val email = request.getQueryString("email").getOrElse("").trim.toLowerCase

    if (email.isEmpty || !email.contains("@")) {
      Future.successful(
        BadRequest(Json.obj(
          "available" -> false,
          "message" -> "Geçerli bir email giriniz."
        ))
      )
    } else {
      userRepository.emailExists(email).map { exists =>
        Ok(Json.obj(
          "available" -> !exists,
          "message" -> {
            if (exists) "Bu email zaten kullanılıyor."
            else "Bu email kullanılabilir."
          }
        ))
      }
    }
  }

  def register = Action.async { implicit request =>
    RegisterForm.form.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(
          BadRequest(views.html.register(formWithErrors))
        )
      },
      data => {
        val dto = RegisterRequest(
          username = data.username,
          email = data.email,
          password = data.password
        )

        authService.register(dto).map { _ =>
          Redirect(routes.AuthController.loginPage())
            .flashing("success" -> "Register successful. Please login.")
        }.recover {
          case ex: RuntimeException =>
            val formWithError =
              RegisterForm.form
                .fill(data)
                .withGlobalError(ex.getMessage)

            BadRequest(views.html.register(formWithError))

          case _ =>
            val formWithError =
              RegisterForm.form
                .fill(data)
                .withGlobalError("Register failed. Please try again.")

            BadRequest(views.html.register(formWithError))
        }
      }
    )
  }

  def login = Action.async { implicit request =>
    LoginForm.form.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(
          BadRequest(views.html.login(formWithErrors))
        )
      },
      data => {
        val dto = LoginRequest(
          email = data.email,
          password = data.password
        )

        authService.login(dto).map {
          case Some(user) =>
            Redirect(routes.TodoController.index())
              .withSession(
                "userId" -> user.id.toString,
                "username" -> user.username,
                "role" -> user.role
              )
              .flashing("success" -> s"Welcome, ${user.username}")

          case None =>
            val formWithError =
              LoginForm.form
                .fill(data)
                .withGlobalError("Email or password is incorrect.")

            BadRequest(views.html.login(formWithError))
        }
      }
    )
  }

  def logout = Action {
    Redirect(routes.AuthController.loginPage())
      .withNewSession
      .flashing("success" -> "Logout successful.")
  }
}