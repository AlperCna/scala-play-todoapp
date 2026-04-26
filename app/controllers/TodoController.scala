package controllers

import dtos._
import forms._
import services.TodoService

import java.util.UUID
import javax.inject._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class TodoController @Inject()(
                                cc: ControllerComponents,
                                todoService: TodoService
                              )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  private def getCurrentUserId(request: RequestHeader): Option[UUID] = {
    request.session.get("userId").flatMap { id =>
      Try(UUID.fromString(id)).toOption
    }
  }

  def index() = Action.async { implicit request =>
    getCurrentUserId(request) match {
      case Some(userId) =>
        val status = request.getQueryString("status").getOrElse("all")
        val search = request.getQueryString("search").getOrElse("")

        val page = request.getQueryString("page")
          .flatMap(value => Try(value.toInt).toOption)
          .getOrElse(1)

        val pageSize = 5

        todoService.getTodosPaged(userId, status, search, page, pageSize).map { todoPage =>
          Ok(views.html.todos(todoPage))
        }

      case None =>
        Future.successful(
          Redirect(routes.AuthController.loginPage())
            .flashing("error" -> "Lütfen önce giriş yapın.")
        )
    }
  }

  def create = Action.async { implicit request =>
    getCurrentUserId(request) match {
      case Some(userId) =>
        TodoCreateForm.form.bindFromRequest().fold(
          formWithErrors => {
            Future.successful(
              Redirect(routes.TodoController.index())
                .flashing("error" -> "Todo oluşturulamadı. Lütfen formu kontrol edin.")
            )
          },
          data => {
            val dto = TodoCreateRequest(
              title = data.title.trim,
              description = data.description.map(_.trim).filter(_.nonEmpty)
            )

            todoService.createTodo(userId, dto).map { createdTodo =>
              Redirect(routes.TodoController.index())
                .flashing("success" -> s"Todo created: ${createdTodo.title}")
            }
          }
        )

      case None =>
        Future.successful(
          Redirect(routes.AuthController.loginPage())
            .flashing("error" -> "Lütfen önce giriş yapın.")
        )
    }
  }

  def edit(id: String) = Action.async { implicit request =>
    getCurrentUserId(request) match {
      case Some(userId) =>
        Try(UUID.fromString(id)).toOption match {
          case Some(todoId) =>
            todoService.getTodoForEdit(userId, todoId).map {
              case Some(todo) =>
                val form = TodoUpdateForm.form.fill(
                  TodoUpdateForm(
                    title = todo.title,
                    description = None,
                    isCompleted = todo.isCompleted
                  )
                )

                Ok(views.html.todoEdit(id, form))

              case None =>
                Redirect(routes.TodoController.index())
                  .flashing("error" -> "Bu todo bulunamadı veya size ait değil.")
            }

          case None =>
            Future.successful(
              Redirect(routes.TodoController.index())
                .flashing("error" -> "Geçersiz todo id.")
            )
        }

      case None =>
        Future.successful(
          Redirect(routes.AuthController.loginPage())
            .flashing("error" -> "Lütfen önce giriş yapın.")
        )
    }
  }

  def update(id: String) = Action.async { implicit request =>
    getCurrentUserId(request) match {
      case Some(userId) =>
        Try(UUID.fromString(id)).toOption match {
          case Some(todoId) =>
            TodoUpdateForm.form.bindFromRequest().fold(
              formWithErrors => {
                Future.successful(
                  BadRequest(views.html.todoEdit(id, formWithErrors))
                )
              },
              data => {
                val dto = TodoUpdateRequest(
                  title = data.title,
                  description = data.description,
                  isCompleted = data.isCompleted
                )

                todoService.updateTodo(userId, todoId, dto).map {
                  case Some(updatedTodo) =>
                    Redirect(routes.TodoController.index())
                      .flashing("success" -> s"Todo updated: ${updatedTodo.title}")

                  case None =>
                    Redirect(routes.TodoController.index())
                      .flashing("error" -> "Bu todo bulunamadı veya size ait değil.")
                }
              }
            )

          case None =>
            Future.successful(
              Redirect(routes.TodoController.index())
                .flashing("error" -> "Geçersiz todo id.")
            )
        }

      case None =>
        Future.successful(
          Redirect(routes.AuthController.loginPage())
            .flashing("error" -> "Lütfen önce giriş yapın.")
        )
    }
  }

  def delete(id: String) = Action.async { implicit request =>
    getCurrentUserId(request) match {
      case Some(userId) =>
        Try(UUID.fromString(id)).toOption match {
          case Some(todoId) =>
            todoService.deleteTodo(userId, todoId).map {
              case true =>
                Redirect(routes.TodoController.index())
                  .flashing("success" -> "Todo deleted.")

              case false =>
                Redirect(routes.TodoController.index())
                  .flashing("error" -> "Bu todo bulunamadı veya size ait değil.")
            }

          case None =>
            Future.successful(
              Redirect(routes.TodoController.index())
                .flashing("error" -> "Geçersiz todo id.")
            )
        }

      case None =>
        Future.successful(
          Redirect(routes.AuthController.loginPage())
            .flashing("error" -> "Lütfen önce giriş yapın.")
        )
    }
  }

  def toggle(id: String) = Action.async { implicit request =>
    getCurrentUserId(request) match {
      case Some(userId) =>
        Try(UUID.fromString(id)).toOption match {
          case Some(todoId) =>
            todoService.toggleTodo(userId, todoId).map {
              case Some(_) =>
                Redirect(routes.TodoController.index())
                  .flashing("success" -> "Todo status updated.")

              case None =>
                Redirect(routes.TodoController.index())
                  .flashing("error" -> "Bu todo bulunamadı veya size ait değil.")
            }

          case None =>
            Future.successful(
              Redirect(routes.TodoController.index())
                .flashing("error" -> "Geçersiz todo id.")
            )
        }

      case None =>
        Future.successful(
          Redirect(routes.AuthController.loginPage())
            .flashing("error" -> "Lütfen önce giriş yapın.")
        )
    }
  }
}