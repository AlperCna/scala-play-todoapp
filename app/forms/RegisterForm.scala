package forms

import play.api.data._
import play.api.data.Forms._

case class RegisterForm(
                         username: String,
                         email: String,
                         password: String
                       )

object RegisterForm {

  val form: Form[RegisterForm] = Form(
    mapping(
      "username" -> nonEmptyText,
      "email" -> email,
      "password" -> nonEmptyText
    )(RegisterForm.apply)(RegisterForm.unapply)
  )
}