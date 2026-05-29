package com.alper.todo.notificationconsumer.infrastructure

import com.alper.todo.notificationconsumer.config.NotificationConsumerDatabaseSettings
import com.alper.todo.notificationconsumer.ports.ProcessedEventStore

import java.sql.{DriverManager, PreparedStatement, Timestamp}
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class JdbcProcessedEventStore(
  databaseSettings: NotificationConsumerDatabaseSettings,
  consumerName: String
)(implicit ec: ExecutionContext) extends ProcessedEventStore {

  Class.forName(databaseSettings.driver)

  override def contains(eventId: UUID): Future[Boolean] = Future {
    val conn = DriverManager.getConnection(
      databaseSettings.url,
      databaseSettings.username,
      databaseSettings.password
    )

    try {
      val stmt = conn.prepareStatement(
        """
          |SELECT COUNT(*) AS total
          |FROM consumer_processed_events
          |WHERE consumer_name = ? AND event_id = ?
          |""".stripMargin
      )
      stmt.setString(1, consumerName)
      stmt.setString(2, eventId.toString)

      val rs = stmt.executeQuery()
      if (rs.next()) rs.getInt("total") > 0 else false
    } finally {
      conn.close()
    }
  }

  override def markProcessed(eventId: UUID, tenantId: UUID): Future[Unit] = Future {
    val conn = DriverManager.getConnection(
      databaseSettings.url,
      databaseSettings.username,
      databaseSettings.password
    )

    try {
      val stmt: PreparedStatement = conn.prepareStatement(
        """
          |INSERT INTO consumer_processed_events (
          |  consumer_name, event_id, tenant_id, processed_at
          |)
          |VALUES (?, ?, ?, ?)
          |""".stripMargin
      )

      stmt.setString(1, consumerName)
      stmt.setString(2, eventId.toString)
      stmt.setString(3, tenantId.toString)
      stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()))
      stmt.executeUpdate()
      ()
    } finally {
      conn.close()
    }
  }
}
