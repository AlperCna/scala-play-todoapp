package controllers

import dtos._
import forms._
import security.{CustomProfile, SecureAction}
import services.{AuditLogService, TodoService}

import javax.inject._
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import java.util.UUID

@Singleton
class TodoController @Inject()(
    cc: ControllerComponents,
    secure: SecureAction,
    todoService: TodoService,
    auditLogService: AuditLogService
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  private val pageSize = 5

  def index() = secure { profile => implicit request =>
    implicit val profileOpt: Option[CustomProfile] = Some(profile)
    val status = request.getQueryString("status").getOrElse("all")
    val search = request.getQueryString("search").getOrElse("")
    val page   = parsePage(request)

    todoService.getTodosPaged(profile.getUserId, status, search, page, pageSize).map { todoPage =>
      Ok(views.html.todos(todoPage, TodoCreateForm.form))
    }
  }

  def create = secure { profile => implicit request =>
    implicit val profileOpt: Option[CustomProfile] = Some(profile)
    TodoCreateForm.form.bindFromRequest().fold(
      formWithErrors => {
        val status = request.getQueryString("status").getOrElse("all")
        val search = request.getQueryString("search").getOrElse("")
        val page   = parsePage(request)

        todoService.getTodosPaged(profile.getUserId, status, search, page, pageSize).map { todoPage =>
          BadRequest(views.html.todos(todoPage, formWithErrors))
            .flashing("error" -> "Todo oluşturulamadı. Lütfen formu kontrol edin.")
        }
      },
      data => {
        val dto = TodoCreateRequest(
          title       = data.title.trim,
          description = data.description.map(_.trim).filter(_.nonEmpty),
          dueDate     = data.dueDate
        )

        todoService.createTodo(profile.getUserId, profile.getTenantId, dto).flatMap { created =>
          auditLogService
            .log(userId = Some(profile.getUserId), tenantId = Some(profile.getTenantId),
              action = s"TODO_CREATED: ${created.title}", request = request)
            .map(_ => Redirect(routes.TodoController.index()).flashing("success" -> s"Todo created: ${created.title}"))
        }.recover { case _ =>
          Redirect(routes.TodoController.index()).flashing("error" -> "Todo oluşturulurken beklenmeyen bir hata oluştu.")
        }
      }
    )
  }

  def edit(id: String) = secure { profile => implicit request =>
    implicit val profileOpt: Option[CustomProfile] = Some(profile)
    Try(UUID.fromString(id)).toOption match {
      case Some(todoId) =>
        todoService.getTodoForEdit(profile.getUserId, todoId).map {
          case Some(todo) =>
            val form = TodoUpdateForm.form.fill(
              TodoUpdateForm(title = todo.title, description = todo.description,
                isCompleted = todo.isCompleted, dueDate = todo.dueDate)
            )
            Ok(views.html.todoEdit(id, form))

          case None =>
            Redirect(routes.TodoController.index()).flashing("error" -> "Bu todo bulunamadı veya size ait değil.")
        }

      case None =>
        Future.successful(Redirect(routes.TodoController.index()).flashing("error" -> "Geçersiz todo id."))
    }
  }

  def update(id: String) = secure { profile => implicit request =>
    implicit val profileOpt: Option[CustomProfile] = Some(profile)
    Try(UUID.fromString(id)).toOption match {
      case Some(todoId) =>
        TodoUpdateForm.form.bindFromRequest().fold(
          formWithErrors =>
            Future.successful(
              BadRequest(views.html.todoEdit(id, formWithErrors))
                .flashing("error" -> "Todo güncellenemedi. Lütfen formu kontrol edin.")
            ),
          data => {
            val dto = TodoUpdateRequest(
              title       = data.title.trim,
              description = data.description.map(_.trim).filter(_.nonEmpty),
              isCompleted = data.isCompleted,
              dueDate     = data.dueDate
            )

            todoService.updateTodo(profile.getUserId, todoId, dto).flatMap {
              case Some(updated) =>
                auditLogService
                  .log(userId = Some(profile.getUserId), tenantId = Some(profile.getTenantId),
                    action = s"TODO_UPDATED: ${updated.title}", request = request)
                  .map(_ => Redirect(routes.TodoController.index()).flashing("success" -> s"Todo updated: ${updated.title}"))

              case None =>
                Future.successful(Redirect(routes.TodoController.index()).flashing("error" -> "Bu todo bulunamadı veya size ait değil."))
            }.recover { case _ =>
              Redirect(routes.TodoController.index()).flashing("error" -> "Todo güncellenirken beklenmeyen bir hata oluştu.")
            }
          }
        )

      case None =>
        Future.successful(Redirect(routes.TodoController.index()).flashing("error" -> "Geçersiz todo id."))
    }
  }

  def delete(id: String) = secure { profile => implicit request =>
    Try(UUID.fromString(id)).toOption match {
      case Some(todoId) =>
        todoService.deleteTodo(profile.getUserId, todoId).flatMap {
          case true =>
            auditLogService
              .log(userId = Some(profile.getUserId), tenantId = Some(profile.getTenantId),
                action = s"TODO_DELETED: $id", request = request)
              .map(_ => Redirect(routes.TodoController.index()).flashing("success" -> "Todo deleted."))

          case false =>
            Future.successful(Redirect(routes.TodoController.index()).flashing("error" -> "Bu todo bulunamadı veya size ait değil."))
        }.recover { case _ =>
          Redirect(routes.TodoController.index()).flashing("error" -> "Todo silinirken beklenmeyen bir hata oluştu.")
        }

      case None =>
        Future.successful(Redirect(routes.TodoController.index()).flashing("error" -> "Geçersiz todo id."))
    }
  }

  def toggle(id: String) = secure { profile => implicit request =>
    val isAjax = request.headers.get("X-Requested-With").contains("XMLHttpRequest")

    Try(UUID.fromString(id)).toOption match {
      case Some(todoId) =>
        todoService.toggleTodo(profile.getUserId, todoId).flatMap {
          case Some(updated) =>
            auditLogService
              .log(userId = Some(profile.getUserId), tenantId = Some(profile.getTenantId),
                action = s"TODO_TOGGLED: ${updated.title} -> ${updated.isCompleted}", request = request)
              .map { _ =>
                if (isAjax)
                  Ok(Json.obj("success" -> true, "id" -> updated.id.toString,
                    "title" -> updated.title, "isCompleted" -> updated.isCompleted))
                else
                  Redirect(routes.TodoController.index()).flashing("success" -> "Todo status updated.")
              }

          case None =>
            Future.successful(
              if (isAjax) NotFound(Json.obj("success" -> false, "message" -> "Todo bulunamadı veya size ait değil."))
              else Redirect(routes.TodoController.index()).flashing("error" -> "Bu todo bulunamadı veya size ait değil.")
            )
        }.recover { case _ =>
          if (isAjax) InternalServerError(Json.obj("success" -> false, "message" -> "Beklenmeyen bir hata oluştu."))
          else Redirect(routes.TodoController.index()).flashing("error" -> "Todo durumu güncellenirken beklenmeyen bir hata oluştu.")
        }

      case None =>
        Future.successful(
          if (isAjax) BadRequest(Json.obj("success" -> false, "message" -> "Geçersiz todo id."))
          else Redirect(routes.TodoController.index()).flashing("error" -> "Geçersiz todo id.")
        )
    }
  }

  private def parsePage(request: RequestHeader): Int =
    request.getQueryString("page")
      .flatMap(v => Try(v.toInt).toOption)
      .filter(_ > 0)
      .getOrElse(1)
}
