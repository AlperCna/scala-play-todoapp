package forms

import play.api.data._
import play.api.data.Forms._

case class RegisterForm(
                         username: String,
                         email: String,
                         password: String,
                         confirmPassword: String
                       )

object RegisterForm {

  val form: Form[RegisterForm] = Form(
    mapping(
      "username" -> nonEmptyText(minLength = 3, maxLength = 50),
      "email" -> email,
      "password" -> nonEmptyText(minLength = 6),
      "confirmPassword" -> nonEmptyText
    )(RegisterForm.apply)(RegisterForm.unapply)
      .verifying("Şifreler eşleşmiyor", data =>
        data.password == data.confirmPassword
      )
  )
}