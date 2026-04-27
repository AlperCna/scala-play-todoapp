package repositories

import models.AuditLog
import play.api.db.Database

import java.sql.{ResultSet, Types}
import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuditLogRepositoryImpl @Inject()(
                                        db: Database
                                      )(implicit ec: ExecutionContext) extends AuditLogRepository {

  private def mapAuditLog(rs: ResultSet): AuditLog = {
    AuditLog(
      id = UUID.fromString(rs.getString("id")),
      userId = Option(rs.getString("user_id")).map(UUID.fromString),
      action = rs.getString("action"),
      ipAddress = Option(rs.getString("ip_address")),
      userAgent = Option(rs.getString("user_agent")),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime
    )
  }

  override def create(auditLog: AuditLog): Future[AuditLog] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |INSERT INTO audit_logs (
          | id, user_id, action, ip_address, user_agent, created_at
          |)
          |VALUES (?, ?, ?, ?, ?, ?)
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)

      stmt.setString(1, auditLog.id.toString)

      auditLog.userId match {
        case Some(userId) => stmt.setString(2, userId.toString)
        case None         => stmt.setNull(2, Types.VARCHAR)
      }

      stmt.setString(3, auditLog.action)

      auditLog.ipAddress match {
        case Some(ip) => stmt.setString(4, ip)
        case None     => stmt.setNull(4, Types.NVARCHAR)
      }

      auditLog.userAgent match {
        case Some(agent) => stmt.setString(5, agent)
        case None        => stmt.setNull(5, Types.NVARCHAR)
      }

      stmt.setTimestamp(6, java.sql.Timestamp.valueOf(auditLog.createdAt))

      stmt.executeUpdate()
      auditLog
    }
  }

  override def findByUserId(userId: UUID): Future[Seq[AuditLog]] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT id, user_id, action, ip_address, user_agent, created_at
          |FROM audit_logs
          |WHERE user_id = ?
          |ORDER BY created_at DESC
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, userId.toString)

      val rs = stmt.executeQuery()
      val logs = scala.collection.mutable.ListBuffer[AuditLog]()

      while (rs.next()) {
        logs += mapAuditLog(rs)
      }

      logs.toSeq
    }
  }

  override def findRecent(limit: Int): Future[Seq[AuditLog]] = Future {
    db.withConnection { conn =>
      val safeLimit = if (limit < 1) 10 else limit

      val sql =
        """
          |SELECT id, user_id, action, ip_address, user_agent, created_at
          |FROM audit_logs
          |ORDER BY created_at DESC
          |OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setInt(1, safeLimit)

      val rs = stmt.executeQuery()
      val logs = scala.collection.mutable.ListBuffer[AuditLog]()

      while (rs.next()) {
        logs += mapAuditLog(rs)
      }

      logs.toSeq
    }
  }

  override def findPaged(page: Int, pageSize: Int): Future[Seq[AuditLog]] = Future {
    db.withConnection { conn =>
      val safePage = if (page < 1) 1 else page
      val safePageSize = if (pageSize < 1) 10 else pageSize
      val offset = (safePage - 1) * safePageSize

      val sql =
        """
          |SELECT id, user_id, action, ip_address, user_agent, created_at
          |FROM audit_logs
          |ORDER BY created_at DESC
          |OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setInt(1, offset)
      stmt.setInt(2, safePageSize)

      val rs = stmt.executeQuery()
      val logs = scala.collection.mutable.ListBuffer[AuditLog]()

      while (rs.next()) {
        logs += mapAuditLog(rs)
      }

      logs.toSeq
    }
  }

  override def countAll(): Future[Int] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT COUNT(*) AS total
          |FROM audit_logs
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      val rs = stmt.executeQuery()

      if (rs.next()) rs.getInt("total") else 0
    }
  }
}