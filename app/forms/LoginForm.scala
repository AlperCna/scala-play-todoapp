package forms

import play.api.data._
import play.api.data.Forms._

case class LoginForm(
                      email: String,
                      password: String
                    )

object LoginForm {

  val form: Form[LoginForm] = Form(
    mapping(
      "email" -> email,
      "password" -> nonEmptyText(minLength = 6)
    )(LoginForm.apply)(LoginForm.unapply)
  )
}