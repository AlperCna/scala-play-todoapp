package forms

import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

case class TodoCreateForm(
                           title: String,
                           description: Option[String],
                           dueDate: Option[java.time.LocalDateTime]
                         )

object TodoCreateForm {

  val form: Form[TodoCreateForm] = Form(
    mapping(
      "title" -> nonEmptyText(maxLength = 200)
        .verifying("Todo başlığı boş olamaz.", value => value.trim.nonEmpty),

      "description" -> optional(text(maxLength = 1000)),
      "dueDate" -> optional(localDateTime("yyyy-MM-dd'T'HH:mm"))
    )(TodoCreateForm.apply)(TodoCreateForm.unapply)
  )
}