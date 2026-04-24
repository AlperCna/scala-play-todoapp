package services

import dtos.{LoginRequest, RegisterRequest}
import models.User

import scala.concurrent.Future

trait AuthService {
  def register(request: RegisterRequest): Future[User]

  def login(request: LoginRequest): Future[Option[User]]
}