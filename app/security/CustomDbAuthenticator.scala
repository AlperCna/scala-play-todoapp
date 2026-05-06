package security

import dtos.LoginRequest
import org.pac4j.core.context.CallContext
import org.pac4j.core.credentials.{Credentials, UsernamePasswordCredentials}
import org.pac4j.core.credentials.authenticator.Authenticator
import org.pac4j.core.exception.CredentialsException
import services.AuthService

import java.util.Optional
import javax.inject.Inject
import scala.concurrent.Await
import scala.concurrent.duration._

class CustomDbAuthenticator @Inject()(authService: AuthService)
    extends Authenticator {

  override def validate(ctx: CallContext, credentials: Credentials): Optional[Credentials] = {
    val upc = credentials.asInstanceOf[UsernamePasswordCredentials]

    val result = Await.result(
      authService.login(LoginRequest(upc.getUsername, upc.getPassword)),
      5.seconds
    )

    result match {
      case Some(user) =>
        val profile = new CustomProfile()
        profile.setId(user.id.toString)
        profile.addAttribute("appUsername", user.username)
        profile.addAttribute("role", user.role)
        profile.addAttribute("tenantId", user.tenantId.toString)
        upc.setUserProfile(profile)
        Optional.of(upc.asInstanceOf[Credentials])

      case None =>
        throw new CredentialsException("Geçersiz email veya şifre.")
    }
  }
}
