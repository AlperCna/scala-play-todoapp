package forms

import play.api.data._
import play.api.data.Forms._

case class TodoCreateForm(
                           title: String,
                           description: Option[String]
                         )

object TodoCreateForm {

  val form: Form[TodoCreateForm] = Form(
    mapping(
      "title" -> nonEmptyText(maxLength = 200),
      "description" -> optional(text)
    )(TodoCreateForm.apply)(TodoCreateForm.unapply)
  )
}