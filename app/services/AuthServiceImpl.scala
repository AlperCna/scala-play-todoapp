package services

import dtos.{LoginRequest, RegisterRequest}
import models.User
import repositories.UserRepository

import java.time.LocalDateTime
import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthServiceImpl @Inject()(
                                 userRepository: UserRepository,
                                 passwordHasher: PasswordHasher
                               )(implicit ec: ExecutionContext) extends AuthService {

  override def register(request: RegisterRequest): Future[User] = {
    userRepository.findByEmail(request.email).flatMap {
      case Some(_) =>
        Future.failed(new RuntimeException("Bu email zaten kullanılıyor."))

      case None =>
        val user = User(
          id = UUID.randomUUID(),
          username = request.username,
          email = request.email,
          passwordHash = passwordHasher.hash(request.password),
          role = "USER",
          createdAt = LocalDateTime.now(),
          updatedAt = None,
          isActive = true
        )

        userRepository.create(user)
    }
  }

  override def login(request: LoginRequest): Future[Option[User]] = {
    userRepository.findByEmail(request.email).map {
      case Some(user)
        if user.isActive && passwordHasher.verify(request.password, user.passwordHash) =>
        Some(user)

      case _ =>
        None
    }
  }
}