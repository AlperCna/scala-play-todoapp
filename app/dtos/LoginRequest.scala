package dtos

case class LoginRequest(
                         email: String,
                         password: String
                       )