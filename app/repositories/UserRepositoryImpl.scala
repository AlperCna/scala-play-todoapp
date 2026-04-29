package repositories

import models.User
import play.api.db.Database

import java.sql.{ResultSet, Types}
import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserRepositoryImpl @Inject()(
                                    db: Database
                                  )(implicit ec: ExecutionContext) extends UserRepository {

  private def mapUser(rs: ResultSet): User = {
    User(
      id = UUID.fromString(rs.getString("id")),
      username = rs.getString("username"),
      email = rs.getString("email"),
      passwordHash = rs.getString("password_hash"),
      role = rs.getString("role"),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime,
      updatedAt = Option(rs.getTimestamp("updated_at")).map(_.toLocalDateTime),
      isActive = rs.getBoolean("is_active"),
      tenantId = UUID.fromString(rs.getString("tenant_id"))
    )
  }

  override def findByEmail(email: String): Future[Option[User]] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT id, username, email, password_hash, role, created_at, updated_at, is_active, tenant_id
          |FROM users
          |WHERE email = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, email.trim.toLowerCase)

      val rs = stmt.executeQuery()
      if (rs.next()) Some(mapUser(rs)) else None
    }
  }

  override def findById(id: UUID): Future[Option[User]] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT id, username, email, password_hash, role, created_at, updated_at, is_active, tenant_id
          |FROM users
          |WHERE id = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, id.toString)

      val rs = stmt.executeQuery()
      if (rs.next()) Some(mapUser(rs)) else None
    }
  }

  override def create(user: User): Future[User] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |INSERT INTO users (
          | id, username, email, password_hash, role, created_at, updated_at, is_active, tenant_id
          |)
          |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, user.id.toString)
      stmt.setString(2, user.username)
      stmt.setString(3, user.email.trim.toLowerCase)
      stmt.setString(4, user.passwordHash)
      stmt.setString(5, user.role)
      stmt.setTimestamp(6, java.sql.Timestamp.valueOf(user.createdAt))

      user.updatedAt match {
        case Some(value) => stmt.setTimestamp(7, java.sql.Timestamp.valueOf(value))
        case None        => stmt.setNull(7, Types.TIMESTAMP)
      }

      stmt.setBoolean(8, user.isActive)
      stmt.setString(9, user.tenantId.toString)

      stmt.executeUpdate()
      user
    }
  }

  override def emailExists(email: String): Future[Boolean] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT COUNT(*) AS total
          |FROM users
          |WHERE email = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, email.trim.toLowerCase)

      val rs = stmt.executeQuery()
      if (rs.next()) rs.getInt("total") > 0 else false
    }
  }

  override def countAll(tenantId: UUID, search: String): Future[Int] = Future {
    db.withConnection { conn =>
      val normalizedSearch = search.trim
      val hasSearch = normalizedSearch.nonEmpty

      val searchCondition =
        if (hasSearch) {
          "AND (username LIKE ? OR email LIKE ?)"
        } else {
          ""
        }

      val sql =
        s"""
           |SELECT COUNT(*) AS total
           |FROM users
           |WHERE tenant_id = ?
           |$searchCondition
           |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, tenantId.toString)

      if (hasSearch) {
        val keyword = s"%$normalizedSearch%"
        stmt.setString(2, keyword)
        stmt.setString(3, keyword)
      }

      val rs = stmt.executeQuery()
      if (rs.next()) rs.getInt("total") else 0
    }
  }

  override def countActiveUsers(tenantId: UUID): Future[Int] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT COUNT(*) AS total
          |FROM users
          |WHERE is_active = 1 AND tenant_id = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, tenantId.toString)
      val rs = stmt.executeQuery()

      if (rs.next()) rs.getInt("total") else 0
    }
  }

  override def countPassiveUsers(tenantId: UUID): Future[Int] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT COUNT(*) AS total
          |FROM users
          |WHERE is_active = 0 AND tenant_id = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, tenantId.toString)
      val rs = stmt.executeQuery()

      if (rs.next()) rs.getInt("total") else 0
    }
  }

  override def findAllPaged(
                             tenantId: UUID,
                             search: String,
                             page: Int,
                             pageSize: Int
                           ): Future[Seq[User]] = Future {
    db.withConnection { conn =>
      val normalizedSearch = search.trim
      val hasSearch = normalizedSearch.nonEmpty

      val safePage = if (page < 1) 1 else page
      val safePageSize = if (pageSize < 1) 10 else pageSize
      val offset = (safePage - 1) * safePageSize

      val searchCondition =
        if (hasSearch) {
          "AND (username LIKE ? OR email LIKE ?)"
        } else {
          ""
        }

      val sql =
        s"""
           |SELECT id, username, email, password_hash, role, created_at, updated_at, is_active, tenant_id
           |FROM users
           |WHERE tenant_id = ?
           |$searchCondition
           |ORDER BY created_at DESC
           |OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
           |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, tenantId.toString)

      if (hasSearch) {
        val keyword = s"%$normalizedSearch%"
        stmt.setString(2, keyword)
        stmt.setString(3, keyword)
        stmt.setInt(4, offset)
        stmt.setInt(5, safePageSize)
      } else {
        stmt.setInt(2, offset)
        stmt.setInt(3, safePageSize)
      }

      val rs = stmt.executeQuery()
      val users = scala.collection.mutable.ListBuffer[User]()

      while (rs.next()) {
        users += mapUser(rs)
      }

      users.toSeq
    }
  }

  override def setActive(userId: UUID, isActive: Boolean): Future[Boolean] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |UPDATE users
          |SET is_active = ?,
          |    updated_at = ?
          |WHERE id = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setBoolean(1, isActive)
      stmt.setTimestamp(2, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()))
      stmt.setString(3, userId.toString)

      stmt.executeUpdate() > 0
    }
  }
}