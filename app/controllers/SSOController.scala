package controllers

import services.{AuditLogService, AuthService}

import java.util.UUID
import javax.inject._
import play.api.Configuration
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class SSOController @Inject()(
                               cc: ControllerComponents,
                               ws: WSClient,
                               config: Configuration,
                               authService: AuthService,
                               auditLogService: AuditLogService
                             )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  // Google OAuth2 Config
  private val googleClientId = config.get[String]("google.oauth.clientId")
  private val googleClientSecret = config.get[String]("google.oauth.clientSecret")
  private val googleCallbackUrl = config.get[String]("google.oauth.callbackUrl")
  private val googleAuthUrl = config.get[String]("google.oauth.authUrl")
  private val googleTokenUrl = config.get[String]("google.oauth.tokenUrl")
  private val googleUserInfoUrl = config.get[String]("google.oauth.userInfoUrl")

  // Microsoft OAuth2 Config
  private val msClientId = config.get[String]("microsoft.oauth.clientId")
  private val msClientSecret = config.get[String]("microsoft.oauth.clientSecret")
  private val msCallbackUrl = config.get[String]("microsoft.oauth.callbackUrl")
  private val msAuthUrl = config.get[String]("microsoft.oauth.authUrl")
  private val msTokenUrl = config.get[String]("microsoft.oauth.tokenUrl")
  private val msUserInfoUrl = config.get[String]("microsoft.oauth.userInfoUrl")

  // ==================== GOOGLE ====================

  def googleLogin = Action { implicit request =>
    val state = UUID.randomUUID().toString

    val authorizationUrl =
      s"$googleAuthUrl" +
        s"?client_id=$googleClientId" +
        s"&redirect_uri=$googleCallbackUrl" +
        s"&response_type=code" +
        s"&scope=email%20profile" +
        s"&state=$state"

    Redirect(authorizationUrl).withSession(
      request.session + ("oauth_state" -> state)
    )
  }

  def googleCallback = Action.async { implicit request =>
    val sessionState = request.session.get("oauth_state")
    val queryState = request.getQueryString("state")
    val codeOpt = request.getQueryString("code")

    // CSRF koruması: state doğrula
    if (sessionState.isEmpty || queryState.isEmpty || sessionState != queryState) {
      Future.successful(
        Redirect(routes.AuthController.loginPage())
          .flashing("error" -> "Güvenlik doğrulaması başarısız. Lütfen tekrar deneyin.")
          .withSession(request.session - "oauth_state")
      )
    } else {
      codeOpt match {
        case Some(code) =>
          exchangeGoogleCode(code).flatMap {
            case Right(userInfo) =>
              val email = (userInfo \ "email").as[String]
              val name = (userInfo \ "name").asOpt[String].getOrElse(email.split("@").head)

              handleSSOLogin(email, name, "GOOGLE", request)

            case Left(error) =>
              Future.successful(
                Redirect(routes.AuthController.loginPage())
                  .flashing("error" -> s"Google ile giriş başarısız: $error")
                  .withSession(request.session - "oauth_state")
              )
          }

        case None =>
          val error = request.getQueryString("error").getOrElse("Bilinmeyen hata")
          Future.successful(
            Redirect(routes.AuthController.loginPage())
              .flashing("error" -> s"Google girişi iptal edildi: $error")
              .withSession(request.session - "oauth_state")
          )
      }
    }
  }

  private def exchangeGoogleCode(code: String): Future[Either[String, JsValue]] = {
    ws.url(googleTokenUrl)
      .post(Map(
        "client_id" -> googleClientId,
        "client_secret" -> googleClientSecret,
        "code" -> code,
        "redirect_uri" -> googleCallbackUrl,
        "grant_type" -> "authorization_code"
      ))
      .flatMap { tokenResponse =>
        if (tokenResponse.status == 200) {
          val accessToken = (tokenResponse.json \ "access_token").as[String]

          ws.url(googleUserInfoUrl)
            .addHttpHeaders("Authorization" -> s"Bearer $accessToken")
            .get()
            .map { userResponse =>
              if (userResponse.status == 200) {
                Right(userResponse.json)
              } else {
                Left("Kullanıcı bilgileri alınamadı.")
              }
            }
        } else {
          Future.successful(Left("Token alınamadı."))
        }
      }
      .recover {
        case ex: Exception => Left(s"Bağlantı hatası: ${ex.getMessage}")
      }
  }

  // ==================== MICROSOFT ====================

  def microsoftLogin = Action { implicit request =>
    val state = UUID.randomUUID().toString

    val authorizationUrl =
      s"$msAuthUrl" +
        s"?client_id=$msClientId" +
        s"&redirect_uri=$msCallbackUrl" +
        s"&response_type=code" +
        s"&scope=openid%20email%20profile%20User.Read" +
        s"&state=$state"

    Redirect(authorizationUrl).withSession(
      request.session + ("oauth_state" -> state)
    )
  }

  def microsoftCallback = Action.async { implicit request =>
    val sessionState = request.session.get("oauth_state")
    val queryState = request.getQueryString("state")
    val codeOpt = request.getQueryString("code")

    // CSRF koruması: state doğrula
    if (sessionState.isEmpty || queryState.isEmpty || sessionState != queryState) {
      Future.successful(
        Redirect(routes.AuthController.loginPage())
          .flashing("error" -> "Güvenlik doğrulaması başarısız. Lütfen tekrar deneyin.")
          .withSession(request.session - "oauth_state")
      )
    } else {
      codeOpt match {
        case Some(code) =>
          exchangeMicrosoftCode(code).flatMap {
            case Right(userInfo) =>
              val email = (userInfo \ "mail").asOpt[String]
                .orElse((userInfo \ "userPrincipalName").asOpt[String])
                .getOrElse("")
              val name = (userInfo \ "displayName").asOpt[String].getOrElse(email.split("@").head)

              if (email.isEmpty || !email.contains("@")) {
                Future.successful(
                  Redirect(routes.AuthController.loginPage())
                    .flashing("error" -> "Microsoft hesabınızda email bilgisi bulunamadı.")
                    .withSession(request.session - "oauth_state")
                )
              } else {
                handleSSOLogin(email, name, "MICROSOFT", request)
              }

            case Left(error) =>
              Future.successful(
                Redirect(routes.AuthController.loginPage())
                  .flashing("error" -> s"Microsoft ile giriş başarısız: $error")
                  .withSession(request.session - "oauth_state")
              )
          }

        case None =>
          val error = request.getQueryString("error_description")
            .orElse(request.getQueryString("error"))
            .getOrElse("Bilinmeyen hata")
          Future.successful(
            Redirect(routes.AuthController.loginPage())
              .flashing("error" -> s"Microsoft girişi iptal edildi: $error")
              .withSession(request.session - "oauth_state")
          )
      }
    }
  }

  private def exchangeMicrosoftCode(code: String): Future[Either[String, JsValue]] = {
    ws.url(msTokenUrl)
      .post(Map(
        "client_id" -> msClientId,
        "client_secret" -> msClientSecret,
        "code" -> code,
        "redirect_uri" -> msCallbackUrl,
        "grant_type" -> "authorization_code",
        "scope" -> "openid email profile User.Read"
      ))
      .flatMap { tokenResponse =>
        if (tokenResponse.status == 200) {
          val accessToken = (tokenResponse.json \ "access_token").as[String]

          ws.url(msUserInfoUrl)
            .addHttpHeaders("Authorization" -> s"Bearer $accessToken")
            .get()
            .map { userResponse =>
              if (userResponse.status == 200) {
                Right(userResponse.json)
              } else {
                Left("Kullanıcı bilgileri alınamadı.")
              }
            }
        } else {
          Future.successful(Left("Token alınamadı."))
        }
      }
      .recover {
        case ex: Exception => Left(s"Bağlantı hatası: ${ex.getMessage}")
      }
  }

  // ==================== ORTAK ====================

  private def handleSSOLogin(
                              email: String,
                              username: String,
                              provider: String,
                              request: Request[AnyContent]
                            ): Future[Result] = {
    authService.loginOrRegisterBySSO(email, username, provider).flatMap { user =>
      auditLogService
        .log(
          userId = Some(user.id),
          tenantId = Some(user.tenantId),
          action = s"SSO_LOGIN_$provider",
          request = request
        )
        .map { _ =>
          Redirect(routes.TodoController.index())
            .withSession(
              "userId" -> user.id.toString,
              "username" -> user.username,
              "role" -> user.role,
              "tenantId" -> user.tenantId.toString
            )
            .flashing("success" -> s"${provider} ile giriş başarılı. Hoş geldiniz, ${user.username}!")
        }
    }.recover {
      case ex: RuntimeException =>
        Redirect(routes.AuthController.loginPage())
          .flashing("error" -> ex.getMessage)
      case _ =>
        Redirect(routes.AuthController.loginPage())
          .flashing("error" -> s"$provider ile giriş sırasında bir hata oluştu.")
    }
  }
}
