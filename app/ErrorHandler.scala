import javax.inject._
import play.api.http.DefaultHttpErrorHandler
import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.routing.Router
import scala.concurrent._

@Singleton
class ErrorHandler @Inject() (
    env: Environment,
    config: Configuration,
    sourceMapper: OptionalSourceMapper,
    router: Provider[Router]
) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    if (statusCode == play.api.http.Status.NOT_FOUND) {
      Future.successful(
        NotFound(views.html.errors.notFound(message)(request, Flash()))
      )
    } else {
      super.onClientError(request, statusCode, message)
    }
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    Future.successful(
      InternalServerError(views.html.errors.serverError(exception.getMessage, Some(exception))(request, Flash()))
    )
  }
}
