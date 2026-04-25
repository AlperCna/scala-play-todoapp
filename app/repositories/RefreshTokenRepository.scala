package repositories

import models.RefreshToken

import java.util.UUID
import scala.concurrent.Future

trait RefreshTokenRepository {
  def create(refreshToken: RefreshToken): Future[RefreshToken]

  def findByTokenHash(tokenHash: String): Future[Option[RefreshToken]]

  def findByUserId(userId: UUID): Future[Seq[RefreshToken]]

  def revoke(tokenHash: String): Future[Boolean]

  def revokeAllByUserId(userId: UUID): Future[Boolean]
}