package services

import java.security.MessageDigest
import java.util.Base64
import javax.inject._

@Singleton
class PasswordHasher {

  def hash(password: String): String = {
    val md = MessageDigest.getInstance("SHA-256")
    val hashedBytes = md.digest(password.getBytes("UTF-8"))
    Base64.getEncoder.encodeToString(hashedBytes)
  }

  def verify(password: String, hashed: String): Boolean = {
    hash(password) == hashed
  }
}