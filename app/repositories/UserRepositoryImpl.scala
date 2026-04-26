package repositories

import models.User
import play.api.db.Database

import java.sql.ResultSet
import java.time.LocalDateTime
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
      isActive = rs.getBoolean("is_active")
    )
  }

  override def findByEmail(email: String): Future[Option[User]] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT id, username, email, password_hash, role, created_at, updated_at, is_active
          |FROM users
          |WHERE email = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, email)

      val rs = stmt.executeQuery()
      if (rs.next()) Some(mapUser(rs)) else None
    }
  }

  override def findById(id: UUID): Future[Option[User]] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT id, username, email, password_hash, role, created_at, updated_at, is_active
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
          | id, username, email, password_hash, role, created_at, updated_at, is_active
          |)
          |VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, user.id.toString)
      stmt.setString(2, user.username)
      stmt.setString(3, user.email)
      stmt.setString(4, user.passwordHash)
      stmt.setString(5, user.role)
      stmt.setTimestamp(6, java.sql.Timestamp.valueOf(user.createdAt))

      user.updatedAt match {
        case Some(value) => stmt.setTimestamp(7, java.sql.Timestamp.valueOf(value))
        case None => stmt.setNull(7, java.sql.Types.TIMESTAMP)
      }

      stmt.setBoolean(8, user.isActive)

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
}