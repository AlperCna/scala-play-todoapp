package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class AdminController @Inject()(cc: ControllerComponents)
  extends AbstractController(cc) {

  def dashboard = Action {
    Ok("Admin dashboard will be rendered here")
  }
}