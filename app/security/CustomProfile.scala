package security

import org.pac4j.core.profile.CommonProfile

import java.util.UUID

class CustomProfile extends CommonProfile {

  def getUserId: UUID = UUID.fromString(getId)

  def getTenantId: UUID =
    UUID.fromString(getAttribute("tenantId").asInstanceOf[String])

  def getAppRole: String =
    Option(getAttribute("role")).map(_.toString).getOrElse("")

  def getAppUsername: String =
    Option(getAttribute("appUsername")).map(_.toString).getOrElse("")
}
