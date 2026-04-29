package repositories

import models.Tenant
import play.api.db.Database

import java.sql.{ResultSet, Types}
import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TenantRepositoryImpl @Inject()(
                                      db: Database
                                    )(implicit ec: ExecutionContext) extends TenantRepository {

  private def mapTenant(rs: ResultSet): Tenant = {
    Tenant(
      id = UUID.fromString(rs.getString("id")),
      name = rs.getString("name"),
      domain = rs.getString("domain"),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime,
      updatedAt = Option(rs.getTimestamp("updated_at")).map(_.toLocalDateTime)
    )
  }

  override def findById(id: UUID): Future[Option[Tenant]] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT id, name, domain, created_at, updated_at
          |FROM tenants
          |WHERE id = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, id.toString)

      val rs = stmt.executeQuery()
      if (rs.next()) Some(mapTenant(rs)) else None
    }
  }

  override def findByDomain(domain: String): Future[Option[Tenant]] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT id, name, domain, created_at, updated_at
          |FROM tenants
          |WHERE domain = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, domain.trim.toLowerCase)

      val rs = stmt.executeQuery()
      if (rs.next()) Some(mapTenant(rs)) else None
    }
  }

  override def create(tenant: Tenant): Future[Tenant] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |INSERT INTO tenants (id, name, domain, created_at, updated_at)
          |VALUES (?, ?, ?, ?, ?)
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, tenant.id.toString)
      stmt.setString(2, tenant.name)
      stmt.setString(3, tenant.domain.trim.toLowerCase)
      stmt.setTimestamp(4, java.sql.Timestamp.valueOf(tenant.createdAt))

      tenant.updatedAt match {
        case Some(value) => stmt.setTimestamp(5, java.sql.Timestamp.valueOf(value))
        case None        => stmt.setNull(5, Types.TIMESTAMP)
      }

      stmt.executeUpdate()
      tenant
    }
  }
}
