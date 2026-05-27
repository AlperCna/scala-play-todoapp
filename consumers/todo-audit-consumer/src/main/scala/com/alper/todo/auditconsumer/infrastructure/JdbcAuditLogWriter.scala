package com.alper.todo.auditconsumer.infrastructure

import com.alper.todo.auditconsumer.config.AuditConsumerDatabaseSettings
import com.alper.todo.auditconsumer.model.AuditProcessingResult.{DuplicateIgnored, Processed}
import com.alper.todo.auditconsumer.model.{AuditCommand, AuditProcessingResult}
import com.alper.todo.auditconsumer.ports.AuditLogWriter

import java.sql.{Connection, DriverManager, PreparedStatement, Timestamp}
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class JdbcAuditLogWriter(
  databaseSettings: AuditConsumerDatabaseSettings,
  consumerName: String
)(implicit ec: ExecutionContext) extends AuditLogWriter {

  Class.forName(databaseSettings.driver)

  override def writeIfNew(command: AuditCommand): Future[AuditProcessingResult] = Future {
    val conn = DriverManager.getConnection(
      databaseSettings.url,
      databaseSettings.username,
      databaseSettings.password
    )

    try {
      conn.setAutoCommit(false)

      if (alreadyProcessed(conn, command.eventId)) {
        conn.rollback()
        DuplicateIgnored
      } else {
        insertAuditLog(conn, command)
        insertProcessedEvent(conn, command.eventId, command.tenantId, command.createdAt)
        conn.commit()
        Processed
      }
    } catch {
      case ex: Throwable =>
        conn.rollback()
        throw ex
    } finally {
      conn.close()
    }
  }

  private def alreadyProcessed(conn: Connection, eventId: UUID): Boolean = {
    val sql =
      """
        |SELECT COUNT(*) AS total
        |FROM consumer_processed_events
        |WHERE consumer_name = ? AND event_id = ?
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setString(1, consumerName)
    stmt.setString(2, eventId.toString)

    val rs = stmt.executeQuery()
    if (rs.next()) rs.getInt("total") > 0 else false
  }

  private def insertAuditLog(conn: Connection, command: AuditCommand): Unit = {
    val sql =
      """
        |INSERT INTO audit_logs (
        | id, user_id, action, ip_address, user_agent, created_at, tenant_id
        |)
        |VALUES (?, ?, ?, ?, ?, ?, ?)
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setString(1, UUID.randomUUID().toString)
    stmt.setString(2, command.userId.toString)
    stmt.setString(3, command.action)
    stmt.setNull(4, java.sql.Types.NVARCHAR)
    stmt.setNull(5, java.sql.Types.NVARCHAR)
    stmt.setTimestamp(6, Timestamp.valueOf(command.createdAt))
    stmt.setString(7, command.tenantId.toString)
    stmt.executeUpdate()
  }

  private def insertProcessedEvent(
    conn: Connection,
    eventId: UUID,
    tenantId: UUID,
    processedAt: LocalDateTime
  ): Unit = {
    val sql =
      """
        |INSERT INTO consumer_processed_events (
        | consumer_name, event_id, tenant_id, processed_at
        |)
        |VALUES (?, ?, ?, ?)
        |""".stripMargin

    val stmt: PreparedStatement = conn.prepareStatement(sql)
    stmt.setString(1, consumerName)
    stmt.setString(2, eventId.toString)
    stmt.setString(3, tenantId.toString)
    stmt.setTimestamp(4, Timestamp.valueOf(processedAt))
    stmt.executeUpdate()
  }
}
