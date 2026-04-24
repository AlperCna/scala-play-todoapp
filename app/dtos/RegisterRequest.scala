package dtos

case class RegisterRequest(
                            username: String,
                            email: String,
                            password: String
                          )