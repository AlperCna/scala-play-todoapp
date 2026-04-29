package services

import dtos.{LoginRequest, RegisterRequest}
import models.User
import java.util.UUID
import scala.concurrent.Future

trait AuthService {
  def register(request: RegisterRequest): Future[User]

  def login(request: LoginRequest): Future[Option[User]]

  def loginOrRegisterBySSO(email: String, username: String, provider: String): Future[User]
}