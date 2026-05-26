package kafka.outbox

import play.api.db.DBApi

import java.sql.{ResultSet, Types}
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TodoOutboxRepositoryImpl @Inject()(
  dbApi: DBApi
)(implicit ec: ExecutionContext) extends TodoOutboxRepository {

  private val db = dbApi.database("default")

  override def create(outboxEvent: TodoOutboxEvent): Future[TodoOutboxEvent] = Future {
    db.withConnection { conn =>
      insertOutbox(conn, outboxEvent)
      outboxEvent
    }
  }

  override def findByAggregateId(aggregateId: UUID): Future[Seq[TodoOutboxEvent]] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT id, aggregate_type, aggregate_id, event_type, event_version,
          |       tenant_id, user_id, payload_json, headers_json, status,
          |       attempt_count, available_at, published_at, last_error, created_at
          |FROM todo_event_outbox
          |WHERE aggregate_id = ?
          |ORDER BY created_at ASC
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, aggregateId.toString)

      val rs = stmt.executeQuery()
      val items = scala.collection.mutable.ListBuffer[TodoOutboxEvent]()

      while (rs.next()) {
        items += mapOutbox(rs)
      }

      items.toSeq
    }
  }

  override def countByStatus(status: String): Future[Int] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT COUNT(*) AS total
          |FROM todo_event_outbox
          |WHERE status = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, status)
      val rs = stmt.executeQuery()

      if (rs.next()) rs.getInt("total") else 0
    }
  }

  override def countByStatusAndTenant(status: String, tenantId: UUID): Future[Int] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT COUNT(*) AS total
          |FROM todo_event_outbox
          |WHERE status = ? AND tenant_id = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, status)
      stmt.setString(2, tenantId.toString)
      val rs = stmt.executeQuery()

      if (rs.next()) rs.getInt("total") else 0
    }
  }

  override def findPublishable(limit: Int, availableBefore: LocalDateTime): Future[Seq[TodoOutboxEvent]] = Future {
    db.withConnection { conn =>
      val safeLimit = if (limit < 1) 10 else limit
      val sql =
        """
          |SELECT id, aggregate_type, aggregate_id, event_type, event_version,
          |       tenant_id, user_id, payload_json, headers_json, status,
          |       attempt_count, available_at, published_at, last_error, created_at
          |FROM todo_event_outbox
          |WHERE status = ? AND available_at <= ?
          |ORDER BY created_at ASC
          |OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, TodoOutboxStatus.Pending)
      stmt.setTimestamp(2, java.sql.Timestamp.valueOf(availableBefore))
      stmt.setInt(3, safeLimit)

      val rs = stmt.executeQuery()
      val items = scala.collection.mutable.ListBuffer[TodoOutboxEvent]()

      while (rs.next()) {
        items += mapOutbox(rs)
      }

      items.toSeq
    }
  }

  override def findFailedByTenantPaged(tenantId: UUID, page: Int, pageSize: Int): Future[Seq[TodoOutboxEvent]] = Future {
    db.withConnection { conn =>
      val safePage = if (page < 1) 1 else page
      val safePageSize = if (pageSize < 1) 10 else pageSize
      val offset = (safePage - 1) * safePageSize

      val sql =
        """
          |SELECT id, aggregate_type, aggregate_id, event_type, event_version,
          |       tenant_id, user_id, payload_json, headers_json, status,
          |       attempt_count, available_at, published_at, last_error, created_at
          |FROM todo_event_outbox
          |WHERE status = ? AND tenant_id = ?
          |ORDER BY created_at DESC
          |OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, TodoOutboxStatus.Failed)
      stmt.setString(2, tenantId.toString)
      stmt.setInt(3, offset)
      stmt.setInt(4, safePageSize)

      val rs = stmt.executeQuery()
      val items = scala.collection.mutable.ListBuffer[TodoOutboxEvent]()

      while (rs.next()) {
        items += mapOutbox(rs)
      }

      items.toSeq
    }
  }

  override def findById(id: UUID): Future[Option[TodoOutboxEvent]] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT id, aggregate_type, aggregate_id, event_type, event_version,
          |       tenant_id, user_id, payload_json, headers_json, status,
          |       attempt_count, available_at, published_at, last_error, created_at
          |FROM todo_event_outbox
          |WHERE id = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, id.toString)
      val rs = stmt.executeQuery()

      if (rs.next()) Some(mapOutbox(rs)) else None
    }
  }

  override def findByIdAndTenant(id: UUID, tenantId: UUID): Future[Option[TodoOutboxEvent]] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT id, aggregate_type, aggregate_id, event_type, event_version,
          |       tenant_id, user_id, payload_json, headers_json, status,
          |       attempt_count, available_at, published_at, last_error, created_at
          |FROM todo_event_outbox
          |WHERE id = ? AND tenant_id = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, id.toString)
      stmt.setString(2, tenantId.toString)
      val rs = stmt.executeQuery()

      if (rs.next()) Some(mapOutbox(rs)) else None
    }
  }

  override def markPublished(id: UUID, publishedAt: LocalDateTime): Future[Boolean] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |UPDATE todo_event_outbox
          |SET status = ?,
          |    published_at = ?,
          |    last_error = NULL
          |WHERE id = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, TodoOutboxStatus.Published)
      stmt.setTimestamp(2, java.sql.Timestamp.valueOf(publishedAt))
      stmt.setString(3, id.toString)

      stmt.executeUpdate() > 0
    }
  }

  override def markFailure(
    id: UUID,
    nextAttemptCount: Int,
    nextAvailableAt: LocalDateTime,
    lastError: String,
    nextStatus: String
  ): Future[Boolean] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |UPDATE todo_event_outbox
          |SET status = ?,
          |    attempt_count = ?,
          |    available_at = ?,
          |    last_error = ?
          |WHERE id = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, nextStatus)
      stmt.setInt(2, nextAttemptCount)
      stmt.setTimestamp(3, java.sql.Timestamp.valueOf(nextAvailableAt))
      stmt.setString(4, lastError.take(1000))
      stmt.setString(5, id.toString)

      stmt.executeUpdate() > 0
    }
  }

  override def resetForReplay(id: UUID, nextAvailableAt: LocalDateTime): Future[Boolean] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |UPDATE todo_event_outbox
          |SET status = ?,
          |    available_at = ?,
          |    published_at = NULL,
          |    last_error = NULL,
          |    attempt_count = 0
          |WHERE id = ? AND status = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, TodoOutboxStatus.Pending)
      stmt.setTimestamp(2, java.sql.Timestamp.valueOf(nextAvailableAt))
      stmt.setString(3, id.toString)
      stmt.setString(4, TodoOutboxStatus.Failed)

      stmt.executeUpdate() > 0
    }
  }

  def insertOutbox(conn: java.sql.Connection, outboxEvent: TodoOutboxEvent): Unit = {
    val sql =
      """
        |INSERT INTO todo_event_outbox (
        | id, aggregate_type, aggregate_id, event_type, event_version,
        | tenant_id, user_id, payload_json, headers_json, status,
        | attempt_count, available_at, published_at, last_error, created_at
        |)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setString(1, outboxEvent.id.toString)
    stmt.setString(2, outboxEvent.aggregateType)
    stmt.setString(3, outboxEvent.aggregateId.toString)
    stmt.setString(4, outboxEvent.eventType)
    stmt.setInt(5, outboxEvent.eventVersion)
    stmt.setString(6, outboxEvent.tenantId.toString)
    stmt.setString(7, outboxEvent.userId.toString)
    stmt.setString(8, outboxEvent.payloadJson)
    stmt.setString(9, outboxEvent.headersJson)
    stmt.setString(10, outboxEvent.status)
    stmt.setInt(11, outboxEvent.attemptCount)
    stmt.setTimestamp(12, java.sql.Timestamp.valueOf(outboxEvent.availableAt))

    outboxEvent.publishedAt match {
      case Some(value) => stmt.setTimestamp(13, java.sql.Timestamp.valueOf(value))
      case None        => stmt.setNull(13, Types.TIMESTAMP)
    }

    outboxEvent.lastError match {
      case Some(value) => stmt.setString(14, value)
      case None        => stmt.setNull(14, Types.NVARCHAR)
    }

    stmt.setTimestamp(15, java.sql.Timestamp.valueOf(outboxEvent.createdAt))
    stmt.executeUpdate()
  }

  private def mapOutbox(rs: ResultSet): TodoOutboxEvent =
    TodoOutboxEvent(
      id = UUID.fromString(rs.getString("id")),
      aggregateType = rs.getString("aggregate_type"),
      aggregateId = UUID.fromString(rs.getString("aggregate_id")),
      eventType = rs.getString("event_type"),
      eventVersion = rs.getInt("event_version"),
      tenantId = UUID.fromString(rs.getString("tenant_id")),
      userId = UUID.fromString(rs.getString("user_id")),
      payloadJson = rs.getString("payload_json"),
      headersJson = rs.getString("headers_json"),
      status = rs.getString("status"),
      attemptCount = rs.getInt("attempt_count"),
      availableAt = rs.getTimestamp("available_at").toLocalDateTime,
      publishedAt = Option(rs.getTimestamp("published_at")).map(_.toLocalDateTime),
      lastError = Option(rs.getString("last_error")),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime
    )
}
