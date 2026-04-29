package services

import dtos.{LoginRequest, RegisterRequest}
import models.{Tenant, User}
import repositories.{TenantRepository, UserRepository}

import java.time.LocalDateTime
import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthServiceImpl @Inject()(
                                 userRepository: UserRepository,
                                 tenantRepository: TenantRepository,
                                 passwordHasher: PasswordHasher
                               )(implicit ec: ExecutionContext) extends AuthService {

  override def register(request: RegisterRequest): Future[User] = {
    userRepository.findByEmail(request.email).flatMap {
      case Some(_) =>
        Future.failed(new RuntimeException("Bu email zaten kullanılıyor."))

      case None =>
        val domain = request.email.split("@").lastOption.getOrElse("unknown.local")

        tenantRepository.findByDomain(domain).flatMap { tenantOpt =>
          val tenantFuture = tenantOpt match {
            case Some(existing) => Future.successful(existing)
            case None =>
              val newTenant = Tenant(
                id = UUID.randomUUID(),
                name = domain.split("\\.").headOption.map(_.capitalize).getOrElse("Default") + " Sirketi",
                domain = domain,
                createdAt = LocalDateTime.now(),
                updatedAt = None
              )
              tenantRepository.create(newTenant)
          }

          tenantFuture.flatMap { tenant =>
            val user = User(
              id = UUID.randomUUID(),
              username = request.username,
              email = request.email,
              passwordHash = passwordHasher.hash(request.password),
              role = "USER",
              createdAt = LocalDateTime.now(),
              updatedAt = None,
              isActive = true,
              tenantId = tenant.id
            )

            userRepository.create(user)
          }
        }
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