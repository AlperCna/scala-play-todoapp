package security

import controllers.routes
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.profile.{ProfileManager, UserProfile}
import org.pac4j.play.PlayWebContext
import play.api.mvc._

import java.util
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.OptionConverters._

class SecureAction @Inject()(
    sessionStore: SessionStore,
    parser: BodyParsers.Default,
    action: DefaultActionBuilder,
    adminAuthorizer: AdminAuthorizer
)(implicit ec: ExecutionContext) {

  // Giriş yapmış kullanıcıya açık action
  def apply(
      f: CustomProfile => Request[AnyContent] => Future[Result]
  ): Action[AnyContent] =
    action.async(parser) { request =>
      getProfile(request) match {
        case Some(profile) => f(profile)(request)
        case None =>
          Future.successful(
            Results.Redirect(routes.AuthController.loginPage())
              .flashing("error" -> "Lütfen önce giriş yapın.")
          )
      }
    }

  // Sadece ADMIN rolüne açık action — AdminAuthorizer üzerinden kontrol edilir
  def admin(
      f: CustomProfile => Request[AnyContent] => Future[Result]
  ): Action[AnyContent] =
    action.async(parser) { request =>
      getProfile(request) match {
        case Some(profile) =>
          val webContext = new PlayWebContext(request)
          val profiles: util.List[UserProfile] =
            util.List.of(profile.asInstanceOf[UserProfile])

          if (adminAuthorizer.isAuthorized(webContext, sessionStore, profiles))
            f(profile)(request)
          else
            Future.successful(
              Results.Redirect(routes.TodoController.index())
                .flashing("error" -> "Bu sayfaya erişim yetkiniz yok.")
            )

        case None =>
          Future.successful(
            Results.Redirect(routes.AuthController.loginPage())
              .flashing("error" -> "Lütfen önce giriş yapın.")
          )
      }
    }

  def getProfile(request: RequestHeader): Option[CustomProfile] = {
    val webContext = new PlayWebContext(request)
    val pm        = new ProfileManager(webContext, sessionStore)
    pm.getProfile.toScala.collect { case p: CustomProfile => p }
  }
}
