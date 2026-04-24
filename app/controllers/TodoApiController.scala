package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class TodoApiController @Inject()(cc: ControllerComponents)
  extends AbstractController(cc) {

  def list = Action {
    Ok("Todo list API will be implemented here")
  }

  def create = Action {
    Ok("Todo create API will be implemented here")
  }

  def update(id: Long) = Action {
    Ok(s"Todo update API will be implemented for id: $id")
  }

  def delete(id: Long) = Action {
    Ok(s"Todo delete API will be implemented for id: $id")
  }

  def toggle(id: Long) = Action {
    Ok(s"Todo toggle API will be implemented for id: $id")
  }
}