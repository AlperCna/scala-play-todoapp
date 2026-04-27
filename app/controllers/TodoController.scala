package controllers

import dtos._
import forms._
import services.TodoService

import java.util.UUID
import javax.inject._
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class TodoController @Inject()(
                                cc: ControllerComponents,
                                todoService: TodoService
                              )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  private val pageSize = 5

  private def getCurrentUserId(request: RequestHeader): Option[UUID] = {
    request.session.get("userId").flatMap { id =>
      Try(UUID.fromString(id)).toOption
    }
  }

  private def getStatus(request: RequestHeader): String = {
    request.getQueryString("status").getOrElse("all")
  }

  private def getSearch(request: RequestHeader): String = {
    request.getQueryString("search").getOrElse("")
  }

  private def getPage(request: RequestHeader): Int = {
    request.getQueryString("page")
      .flatMap(value => Try(value.toInt).toOption)
      .filter(_ > 0)
      .getOrElse(1)
  }

  def index() = Action.async { implicit request =>
    getCurrentUserId(request) match {
      case Some(userId) =>
        val status = getStatus(request)
        val search = getSearch(request)
        val page = getPage(request)

        todoService.getTodosPaged(userId, status, search, page, pageSize).map { todoPage =>
          Ok(views.html.todos(todoPage, TodoCreateForm.form))
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
            val status = getStatus(request)
            val search = getSearch(request)
            val page = getPage(request)

            todoService.getTodosPaged(userId, status, search, page, pageSize).map { todoPage =>
              BadRequest(views.html.todos(todoPage, formWithErrors))
                .flashing("error" -> "Todo oluşturulamadı. Lütfen formu kontrol edin.")
            }
          },
          data => {
            val dto = TodoCreateRequest(
              title = data.title.trim,
              description = data.description.map(_.trim).filter(_.nonEmpty)
            )

            todoService.createTodo(userId, dto).map { createdTodo =>
              Redirect(routes.TodoController.index())
                .flashing("success" -> s"Todo created: ${createdTodo.title}")
            }.recover {
              case _ =>
                Redirect(routes.TodoController.index())
                  .flashing("error" -> "Todo oluşturulurken beklenmeyen bir hata oluştu.")
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
                    description = todo.description,
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
                    .flashing("error" -> "Todo güncellenemedi. Lütfen formu kontrol edin.")
                )
              },
              data => {
                val dto = TodoUpdateRequest(
                  title = data.title.trim,
                  description = data.description.map(_.trim).filter(_.nonEmpty),
                  isCompleted = data.isCompleted
                )

                todoService.updateTodo(userId, todoId, dto).map {
                  case Some(updatedTodo) =>
                    Redirect(routes.TodoController.index())
                      .flashing("success" -> s"Todo updated: ${updatedTodo.title}")

                  case None =>
                    Redirect(routes.TodoController.index())
                      .flashing("error" -> "Bu todo bulunamadı veya size ait değil.")
                }.recover {
                  case _ =>
                    Redirect(routes.TodoController.index())
                      .flashing("error" -> "Todo güncellenirken beklenmeyen bir hata oluştu.")
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
            }.recover {
              case _ =>
                Redirect(routes.TodoController.index())
                  .flashing("error" -> "Todo silinirken beklenmeyen bir hata oluştu.")
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
    val isAjax =
      request.headers.get("X-Requested-With").contains("XMLHttpRequest")

    getCurrentUserId(request) match {
      case Some(userId) =>
        Try(UUID.fromString(id)).toOption match {
          case Some(todoId) =>
            todoService.toggleTodo(userId, todoId).map {
              case Some(updatedTodo) =>
                if (isAjax) {
                  Ok(Json.obj(
                    "success" -> true,
                    "id" -> updatedTodo.id.toString,
                    "title" -> updatedTodo.title,
                    "isCompleted" -> updatedTodo.isCompleted
                  ))
                } else {
                  Redirect(routes.TodoController.index())
                    .flashing("success" -> "Todo status updated.")
                }

              case None =>
                if (isAjax) {
                  NotFound(Json.obj(
                    "success" -> false,
                    "message" -> "Todo bulunamadı veya size ait değil."
                  ))
                } else {
                  Redirect(routes.TodoController.index())
                    .flashing("error" -> "Bu todo bulunamadı veya size ait değil.")
                }
            }.recover {
              case _ =>
                if (isAjax) {
                  InternalServerError(Json.obj(
                    "success" -> false,
                    "message" -> "Todo durumu güncellenirken beklenmeyen bir hata oluştu."
                  ))
                } else {
                  Redirect(routes.TodoController.index())
                    .flashing("error" -> "Todo durumu güncellenirken beklenmeyen bir hata oluştu.")
                }
            }

          case None =>
            Future.successful {
              if (isAjax) {
                BadRequest(Json.obj(
                  "success" -> false,
                  "message" -> "Geçersiz todo id."
                ))
              } else {
                Redirect(routes.TodoController.index())
                  .flashing("error" -> "Geçersiz todo id.")
              }
            }
        }

      case None =>
        Future.successful {
          if (isAjax) {
            Unauthorized(Json.obj(
              "success" -> false,
              "message" -> "Lütfen önce giriş yapın."
            ))
          } else {
            Redirect(routes.AuthController.loginPage())
              .flashing("error" -> "Lütfen önce giriş yapın.")
          }
        }
    }
  }
}