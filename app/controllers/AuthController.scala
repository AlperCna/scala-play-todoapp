package controllers

import dtos._
import forms._
import org.pac4j.core.context.CallContext
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.credentials.UsernamePasswordCredentials
import org.pac4j.core.exception.CredentialsException
import org.pac4j.core.profile.ProfileManager
import org.pac4j.play.PlayWebContext
import repositories.UserRepository
import security.{CustomDbAuthenticator, CustomProfile}
import services.{AuditLogService, AuthService}

import javax.inject._
import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.OptionConverters._

@Singleton
class AuthController @Inject()(
                                cc: ControllerComponents,
                                authService: AuthService,
                                userRepository: UserRepository,
                                auditLogService: AuditLogService,
                                sessionStore: SessionStore,
                                authenticator: CustomDbAuthenticator,
                                ws: WSClient,
                                config: Configuration
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
        BadRequest(Json.obj("available" -> false, "message" -> "Geçerli bir email giriniz."))
      )
    } else {
      userRepository.emailExists(email).map { exists =>
        Ok(Json.obj(
          "available" -> !exists,
          "message"   -> (if (exists) "Bu email zaten kullanılıyor." else "Bu email kullanılabilir.")
        ))
      }
    }
  }

  def register = Action.async { implicit request =>
    RegisterForm.form.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(views.html.register(formWithErrors))),
      data => {
        val dto = RegisterRequest(username = data.username, email = data.email, password = data.password)
        authService.register(dto).flatMap { user =>
          auditLogService
            .log(userId = Some(user.id), tenantId = Some(user.tenantId), action = "USER_REGISTERED", request = request)
            .map(_ => Redirect(routes.AuthController.loginPage()).flashing("success" -> "Register successful. Please login."))
        }.recover {
          case ex: RuntimeException =>
            BadRequest(views.html.register(RegisterForm.form.fill(data).withGlobalError(ex.getMessage)))
          case _ =>
            BadRequest(views.html.register(RegisterForm.form.fill(data).withGlobalError("Register failed. Please try again.")))
        }
      }
    )
  }

  def login = Action.async { implicit request =>
    LoginForm.form.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(views.html.login(formWithErrors))),
      data => {
        Future {
          val credentials = new UsernamePasswordCredentials(data.email, data.password)
          val webContext  = new PlayWebContext(request)
          val callContext = new CallContext(webContext, sessionStore)

          try {
            authenticator.validate(callContext, credentials)

            val profile = credentials.getUserProfile.asInstanceOf[CustomProfile]
            val pm      = new ProfileManager(webContext, sessionStore)
            pm.save(true, profile, false)

            Right((profile, webContext))
          } catch {
            case _: CredentialsException => Left("Email or password is incorrect.")
            case _: Exception            => Left("Login sırasında bir hata oluştu.")
          }
        }.flatMap {
          case Right((profile, webContext)) =>
            auditLogService
              .log(userId = Some(profile.getUserId), tenantId = Some(profile.getTenantId), action = "USER_LOGIN", request = request)
              .map { _ =>

                val n8nUrl = config
                  .getOptional[String]("n8n.webhook.login")
                  .getOrElse("http://localhost:5678/webhook-test/user-login")

                ws.url(n8nUrl)
                  .post(Json.obj(
                    "username"  -> profile.getAppUsername,
                    "email"     -> data.email,
                    "tenantId"  -> profile.getTenantId.toString,
                    "loginTime" -> java.time.LocalDateTime.now().toString,
                    "provider"  -> "FORM"
                  ))
                  .map(_ => ())
                  .recover { case _ => () }

                val result = Redirect(routes.TodoController.index())
                  .flashing("success" -> s"Welcome, ${profile.getAppUsername}")

                webContext.supplementResponse(result)
              }

          case Left(errorMsg) =>
            Future.successful(
              BadRequest(views.html.login(LoginForm.form.fill(data).withGlobalError(errorMsg)))
            )
        }
      }
    )
  }

  def logout = Action.async { implicit request =>
    val webContext = new PlayWebContext(request)
    val pm         = new ProfileManager(webContext, sessionStore)

    val profileOpt      = pm.getProfile.toScala.collect { case p: CustomProfile => p }
    val currentUserId   = profileOpt.map(_.getUserId)
    val currentTenantId = profileOpt.map(_.getTenantId)

    pm.removeProfiles()

    auditLogService
      .log(userId = currentUserId, tenantId = currentTenantId, action = "USER_LOGOUT", request = request)
      .map { _ =>
        val result = Redirect(routes.AuthController.loginPage())
          .withNewSession
          .flashing("success" -> "Logout successful.")
        webContext.supplementResponse(result)
      }
  }
}