package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class TodoPageController @Inject()(cc: ControllerComponents)
  extends AbstractController(cc) {

  def index = Action {
    Ok("Todo page will be rendered here")
  }
}