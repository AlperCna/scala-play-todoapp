package forms

import play.api.data._
import play.api.data.Forms._

case class TodoUpdateForm(
                           title: String,
                           description: Option[String],
                           isCompleted: Boolean
                         )

object TodoUpdateForm {

  val form: Form[TodoUpdateForm] = Form(
    mapping(
      "title" -> nonEmptyText(maxLength = 200)
        .verifying("Todo başlığı boş olamaz.", value => value.trim.nonEmpty),

      "description" -> optional(text(maxLength = 1000)),
      "isCompleted" -> boolean
    )(TodoUpdateForm.apply)(TodoUpdateForm.unapply)
  )
}