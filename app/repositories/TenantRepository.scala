package repositories

import models.Tenant
import java.util.UUID
import scala.concurrent.Future

trait TenantRepository {
  def findById(id: UUID): Future[Option[Tenant]]
  def findByDomain(domain: String): Future[Option[Tenant]]
  def create(tenant: Tenant): Future[Tenant]
}
