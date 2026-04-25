package controllers

import dtos._
import forms._
import javax.inject._
import play.api.mvc._

@Singleton
class TodoController @Inject()(cc: ControllerComponents)
  extends AbstractController(cc) {

  private def isLoggedIn(request: RequestHeader): Boolean = {
    request.session.get("userId").isDefined
  }

  def index() = Action { implicit request =>
    if (isLoggedIn(request)) {
      val todos = List("Scala öğren", "Play Framework çalış", "Todo App bitir")
      Ok(views.html.todos(todos))
    } else {
      Redirect(routes.AuthController.loginPage())
        .flashing("error" -> "Lütfen önce giriş yapın.")
    }
  }

  def create = Action { implicit request =>
    if (!isLoggedIn(request)) {
      Redirect(routes.AuthController.loginPage())
        .flashing("error" -> "Lütfen önce giriş yapın.")
    } else {
      TodoCreateForm.form.bindFromRequest().fold(
        formWithErrors => {
          BadRequest("Todo create form error")
        },
        data => {
          val dto = TodoCreateRequest(
            title = data.title,
            description = data.description
          )

          Redirect(routes.TodoController.index())
            .flashing("success" -> s"Todo created: ${dto.title}")
        }
      )
    }
  }

  def edit(id: String) = Action { implicit request =>
    if (!isLoggedIn(request)) {
      Redirect(routes.AuthController.loginPage())
        .flashing("error" -> "Lütfen önce giriş yapın.")
    } else {
      val form = TodoUpdateForm.form.fill(
        TodoUpdateForm(
          title = "Sample todo",
          description = Some("Sample description"),
          isCompleted = false
        )
      )

      Ok(views.html.todoEdit(id, form))
    }
  }

  def update(id: String) = Action { implicit request =>
    if (!isLoggedIn(request)) {
      Redirect(routes.AuthController.loginPage())
        .flashing("error" -> "Lütfen önce giriş yapın.")
    } else {
      TodoUpdateForm.form.bindFromRequest().fold(
        formWithErrors => {
          BadRequest("Todo update form error")
        },
        data => {
          val dto = TodoUpdateRequest(
            title = data.title,
            description = data.description,
            isCompleted = data.isCompleted
          )

          Redirect(routes.TodoController.index())
            .flashing("success" -> s"Todo updated: ${dto.title}")
        }
      )
    }
  }

  def delete(id: String) = Action { implicit request =>
    if (!isLoggedIn(request)) {
      Redirect(routes.AuthController.loginPage())
        .flashing("error" -> "Lütfen önce giriş yapın.")
    } else {
      Redirect(routes.TodoController.index())
        .flashing("success" -> s"Todo deleted: $id")
    }
  }

  def toggle(id: String) = Action { implicit request =>
    if (!isLoggedIn(request)) {
      Redirect(routes.AuthController.loginPage())
        .flashing("error" -> "Lütfen önce giriş yapın.")
    } else {
      Redirect(routes.TodoController.index())
        .flashing("success" -> s"Todo toggled: $id")
    }
  }
}