package controllers

import dtos._
import forms._
import javax.inject._
import play.api.mvc._

@Singleton
class TodoController @Inject()(cc: ControllerComponents)
  extends AbstractController(cc) {

  def index = Action { implicit request =>
    val todos = Seq(
      "Scala öğren",
      "Play Framework çalış",
      "Todo App bitir"
    )

    Ok(views.html.todos(todos))
  }

  def create = Action { implicit request =>
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

  def edit(id: String) = Action { implicit request =>
    val form = TodoUpdateForm.form.fill(
      TodoUpdateForm(
        title = "Sample todo",
        description = Some("Sample description"),
        isCompleted = false
      )
    )

    Ok(views.html.todoEdit(id, form)(request, request.flash))
  }

  def update(id: String) = Action { implicit request =>
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

  def delete(id: String) = Action {
    Redirect(routes.TodoController.index())
      .flashing("success" -> s"Todo deleted: $id")
  }

  def toggle(id: String) = Action {
    Redirect(routes.TodoController.index())
      .flashing("success" -> s"Todo toggled: $id")
  }
}