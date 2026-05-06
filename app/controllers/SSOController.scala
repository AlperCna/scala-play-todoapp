package controllers

import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.profile.ProfileManager
import org.pac4j.play.PlayWebContext
import security.CustomProfile
import services.{AuditLogService, AuthService}

import java.util.UUID
import javax.inject._
import play.api.Configuration
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.OptionConverters._

@Singleton
class SSOController @Inject()(
    cc: ControllerComponents,
    ws: WSClient,
    config: Configuration,
    authService: AuthService,
    auditLogService: AuditLogService,
    sessionStore: SessionStore
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  // Google OAuth2 Config
  private val googleClientId     = config.get[String]("google.oauth.clientId")
  private val googleClientSecret = config.get[String]("google.oauth.clientSecret")
  private val googleCallbackUrl  = config.get[String]("google.oauth.callbackUrl")
  private val googleAuthUrl      = config.get[String]("google.oauth.authUrl")
  private val googleTokenUrl     = config.get[String]("google.oauth.tokenUrl")
  private val googleUserInfoUrl  = config.get[String]("google.oauth.userInfoUrl")

  // Microsoft OAuth2 Config
  private val msClientId     = config.get[String]("microsoft.oauth.clientId")
  private val msClientSecret = config.get[String]("microsoft.oauth.clientSecret")
  private val msCallbackUrl  = config.get[String]("microsoft.oauth.callbackUrl")
  private val msAuthUrl      = config.get[String]("microsoft.oauth.authUrl")
  private val msTokenUrl     = config.get[String]("microsoft.oauth.tokenUrl")
  private val msUserInfoUrl  = config.get[String]("microsoft.oauth.userInfoUrl")

  // ==================== GOOGLE ====================

  def googleLogin = Action { implicit request =>
    val state      = UUID.randomUUID().toString
    val webContext = new PlayWebContext(request)

    // OAuth state PAC4J SessionStore'a yazılır (AES şifreli cookie)
    sessionStore.set(webContext, "oauth_state", state)

    val authorizationUrl =
      s"$googleAuthUrl?client_id=$googleClientId" +
        s"&redirect_uri=$googleCallbackUrl" +
        s"&response_type=code&scope=email%20profile&state=$state"

    webContext.supplementResponse(Redirect(authorizationUrl))
  }

  def googleCallback = Action.async { implicit request =>
    val webContext   = new PlayWebContext(request)

    // OAuth state PAC4J SessionStore'dan okunur
    val sessionState = sessionStore.get(webContext, "oauth_state").toScala.map(_.toString)
    val queryState   = request.getQueryString("state")
    val codeOpt      = request.getQueryString("code")

    // State kullanıldıktan sonra temizlenir
    sessionStore.set(webContext, "oauth_state", null)

    if (sessionState.isEmpty || queryState.isEmpty || sessionState != queryState) {
      val result = Redirect(routes.AuthController.loginPage())
        .flashing("error" -> "Güvenlik doğrulaması başarısız. Lütfen tekrar deneyin.")
      Future.successful(webContext.supplementResponse(result))
    } else {
      codeOpt match {
        case Some(code) =>
          exchangeGoogleCode(code).flatMap {
            case Right(userInfo) =>
              val email = (userInfo \ "email").as[String]
              val name  = (userInfo \ "name").asOpt[String].getOrElse(email.split("@").head)
              handleSSOLogin(email, name, "GOOGLE", request, webContext)

            case Left(error) =>
              val result = Redirect(routes.AuthController.loginPage())
                .flashing("error" -> s"Google ile giriş başarısız: $error")
              Future.successful(webContext.supplementResponse(result))
          }

        case None =>
          val error  = request.getQueryString("error").getOrElse("Bilinmeyen hata")
          val result = Redirect(routes.AuthController.loginPage())
            .flashing("error" -> s"Google girişi iptal edildi: $error")
          Future.successful(webContext.supplementResponse(result))
      }
    }
  }

  private def exchangeGoogleCode(code: String): Future[Either[String, JsValue]] = {
    ws.url(googleTokenUrl)
      .post(Map(
        "client_id"     -> googleClientId,
        "client_secret" -> googleClientSecret,
        "code"          -> code,
        "redirect_uri"  -> googleCallbackUrl,
        "grant_type"    -> "authorization_code"
      ))
      .flatMap { tokenResponse =>
        if (tokenResponse.status == 200) {
          val accessToken = (tokenResponse.json \ "access_token").as[String]
          ws.url(googleUserInfoUrl)
            .addHttpHeaders("Authorization" -> s"Bearer $accessToken")
            .get()
            .map { userResponse =>
              if (userResponse.status == 200) Right(userResponse.json)
              else Left("Kullanıcı bilgileri alınamadı.")
            }
        } else {
          Future.successful(Left("Token alınamadı."))
        }
      }
      .recover { case ex: Exception => Left(s"Bağlantı hatası: ${ex.getMessage}") }
  }

  // ==================== MICROSOFT ====================

  def microsoftLogin = Action { implicit request =>
    val state      = UUID.randomUUID().toString
    val webContext = new PlayWebContext(request)

    // OAuth state PAC4J SessionStore'a yazılır (AES şifreli cookie)
    sessionStore.set(webContext, "oauth_state", state)

    val authorizationUrl =
      s"$msAuthUrl?client_id=$msClientId" +
        s"&redirect_uri=$msCallbackUrl" +
        s"&response_type=code&scope=openid%20email%20profile%20User.Read&state=$state"

    webContext.supplementResponse(Redirect(authorizationUrl))
  }

  def microsoftCallback = Action.async { implicit request =>
    val webContext   = new PlayWebContext(request)

    // OAuth state PAC4J SessionStore'dan okunur
    val sessionState = sessionStore.get(webContext, "oauth_state").toScala.map(_.toString)
    val queryState   = request.getQueryString("state")
    val codeOpt      = request.getQueryString("code")

    // State kullanıldıktan sonra temizlenir
    sessionStore.set(webContext, "oauth_state", null)

    if (sessionState.isEmpty || queryState.isEmpty || sessionState != queryState) {
      val result = Redirect(routes.AuthController.loginPage())
        .flashing("error" -> "Güvenlik doğrulaması başarısız. Lütfen tekrar deneyin.")
      Future.successful(webContext.supplementResponse(result))
    } else {
      codeOpt match {
        case Some(code) =>
          exchangeMicrosoftCode(code).flatMap {
            case Right(userInfo) =>
              val email = (userInfo \ "mail").asOpt[String]
                .orElse((userInfo \ "userPrincipalName").asOpt[String])
                .getOrElse("")
              val name  = (userInfo \ "displayName").asOpt[String].getOrElse(email.split("@").head)

              if (email.isEmpty || !email.contains("@")) {
                val result = Redirect(routes.AuthController.loginPage())
                  .flashing("error" -> "Microsoft hesabınızda email bilgisi bulunamadı.")
                Future.successful(webContext.supplementResponse(result))
              } else {
                handleSSOLogin(email, name, "MICROSOFT", request, webContext)
              }

            case Left(error) =>
              val result = Redirect(routes.AuthController.loginPage())
                .flashing("error" -> s"Microsoft ile giriş başarısız: $error")
              Future.successful(webContext.supplementResponse(result))
          }

        case None =>
          val error  = request.getQueryString("error_description")
            .orElse(request.getQueryString("error"))
            .getOrElse("Bilinmeyen hata")
          val result = Redirect(routes.AuthController.loginPage())
            .flashing("error" -> s"Microsoft girişi iptal edildi: $error")
          Future.successful(webContext.supplementResponse(result))
      }
    }
  }

  private def exchangeMicrosoftCode(code: String): Future[Either[String, JsValue]] = {
    ws.url(msTokenUrl)
      .post(Map(
        "client_id"     -> msClientId,
        "client_secret" -> msClientSecret,
        "code"          -> code,
        "redirect_uri"  -> msCallbackUrl,
        "grant_type"    -> "authorization_code",
        "scope"         -> "openid email profile User.Read"
      ))
      .flatMap { tokenResponse =>
        if (tokenResponse.status == 200) {
          val accessToken = (tokenResponse.json \ "access_token").as[String]
          ws.url(msUserInfoUrl)
            .addHttpHeaders("Authorization" -> s"Bearer $accessToken")
            .get()
            .map { userResponse =>
              if (userResponse.status == 200) Right(userResponse.json)
              else Left("Kullanıcı bilgileri alınamadı.")
            }
        } else {
          Future.successful(Left("Token alınamadı."))
        }
      }
      .recover { case ex: Exception => Left(s"Bağlantı hatası: ${ex.getMessage}") }
  }

  // ==================== ORTAK ====================

  private def handleSSOLogin(
      email: String,
      username: String,
      provider: String,
      request: Request[AnyContent],
      webContext: PlayWebContext
  ): Future[Result] = {
    authService.loginOrRegisterBySSO(email, username, provider).flatMap { user =>
      auditLogService
        .log(
          userId   = Some(user.id),
          tenantId = Some(user.tenantId),
          action   = s"SSO_LOGIN_$provider",
          request  = request
        )
        .map { _ =>
          // Form login ile tamamen aynı PAC4J akışı
          val profile = new CustomProfile()
          profile.setId(user.id.toString)
          profile.addAttribute("appUsername", user.username)
          profile.addAttribute("role", user.role)
          profile.addAttribute("tenantId", user.tenantId.toString)

          val pm = new ProfileManager(webContext, sessionStore)
          pm.save(true, profile, false)  // AES şifreli cookie'ye yazar

          val result = Redirect(routes.TodoController.index())
            .flashing("success" -> s"$provider ile giriş başarılı. Hoş geldiniz, ${user.username}!")
          webContext.supplementResponse(result)
        }
    }.recover {
      case ex: RuntimeException =>
        Redirect(routes.AuthController.loginPage()).flashing("error" -> ex.getMessage)
      case _ =>
        Redirect(routes.AuthController.loginPage())
          .flashing("error" -> s"$provider ile giriş sırasında bir hata oluştu.")
    }
  }
}
