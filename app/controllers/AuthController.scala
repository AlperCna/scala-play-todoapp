package controllers

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

  def login = Action {
    Ok("Login API will be implemented here")
  }

  def register = Action {
    Ok("Register API will be implemented here")
  }

  def logout = Action {
    Ok("Logout will be implemented here")
  }
}