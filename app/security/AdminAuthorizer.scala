package security

import org.pac4j.core.authorization.authorizer.ProfileAuthorizer
import org.pac4j.core.context.WebContext
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.profile.UserProfile

import java.util

class AdminAuthorizer extends ProfileAuthorizer {

  override def isAuthorized(
      context: WebContext,
      sessionStore: SessionStore,
      profiles: util.List[UserProfile]
  ): Boolean = isAnyAuthorized(context, sessionStore, profiles)

  override def isProfileAuthorized(
      context: WebContext,
      sessionStore: SessionStore,
      profile: UserProfile
  ): Boolean =
    Option(profile.getAttribute("role")).exists(_.toString == "ADMIN")
}
