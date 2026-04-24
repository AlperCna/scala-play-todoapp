package services

import models.User

trait TokenService {
  def generateAccessToken(user: User): String

  def generateRefreshToken(user: User): String
}