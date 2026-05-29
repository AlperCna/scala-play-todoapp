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
          |       attempt_count, available_at, published_at, last_error, created_at,
          |       replay_count, last_replayed_at, last_replayed_by_user_id
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

  override def countFailedByTenant(
    tenantId: UUID,
    filters: TodoOutboxReplayFilters
  ): Future[Int] = Future {
    db.withConnection { conn =>
      val sql =
        s"""
           |SELECT COUNT(*) AS total
           |FROM todo_event_outbox
           |${failedFilterClause(filters)}
           |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      bindFailedFilterParams(stmt, tenantId, filters)
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
          |       attempt_count, available_at, published_at, last_error, created_at,
          |       replay_count, last_replayed_at, last_replayed_by_user_id
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

  override def findFailedByTenantPaged(
    tenantId: UUID,
    page: Int,
    pageSize: Int,
    filters: TodoOutboxReplayFilters
  ): Future[Seq[TodoOutboxEvent]] = Future {
    db.withConnection { conn =>
      val safePage = if (page < 1) 1 else page
      val safePageSize = if (pageSize < 1) 10 else pageSize
      val offset = (safePage - 1) * safePageSize

      val sql =
        s"""
          |SELECT id, aggregate_type, aggregate_id, event_type, event_version,
          |       tenant_id, user_id, payload_json, headers_json, status,
          |       attempt_count, available_at, published_at, last_error, created_at,
          |       replay_count, last_replayed_at, last_replayed_by_user_id
          |FROM todo_event_outbox
          |${failedFilterClause(filters)}
          |ORDER BY created_at DESC
          |OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      val nextIndex = bindFailedFilterParams(stmt, tenantId, filters)
      stmt.setInt(nextIndex, offset)
      stmt.setInt(nextIndex + 1, safePageSize)

      val rs = stmt.executeQuery()
      val items = scala.collection.mutable.ListBuffer[TodoOutboxEvent]()

      while (rs.next()) {
        items += mapOutbox(rs)
      }

      items.toSeq
    }
  }

  override def findFailedByTenantForReplay(
    tenantId: UUID,
    filters: TodoOutboxReplayFilters,
    limit: Int
  ): Future[Seq[TodoOutboxEvent]] = Future {
    db.withConnection { conn =>
      val safeLimit = if (limit < 1) 50 else limit
      val sql =
        s"""
           |SELECT id, aggregate_type, aggregate_id, event_type, event_version,
           |       tenant_id, user_id, payload_json, headers_json, status,
           |       attempt_count, available_at, published_at, last_error, created_at,
           |       replay_count, last_replayed_at, last_replayed_by_user_id
           |FROM todo_event_outbox
           |${failedFilterClause(filters)}
           |ORDER BY created_at ASC
           |OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
           |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      val nextIndex = bindFailedFilterParams(stmt, tenantId, filters)
      stmt.setInt(nextIndex, safeLimit)

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
          |       attempt_count, available_at, published_at, last_error, created_at,
          |       replay_count, last_replayed_at, last_replayed_by_user_id
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
          |       attempt_count, available_at, published_at, last_error, created_at,
          |       replay_count, last_replayed_at, last_replayed_by_user_id
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

  override def replayFailedEvents(
    events: Seq[TodoOutboxEvent],
    replayedByUserId: UUID,
    replayedAt: LocalDateTime,
    replayMode: String,
    filterSummary: Option[String]
  ): Future[Int] = Future {
    if (events.isEmpty) {
      0
    } else {
      db.withTransaction { conn =>
        val updateSql =
          """
            |UPDATE todo_event_outbox
            |SET status = ?,
            |    available_at = ?,
            |    published_at = NULL,
            |    last_error = NULL,
            |    attempt_count = 0,
            |    replay_count = replay_count + 1,
            |    last_replayed_at = ?,
            |    last_replayed_by_user_id = ?
            |WHERE id = ? AND tenant_id = ? AND status = ?
            |""".stripMargin

        val insertLogSql =
          """
            |INSERT INTO todo_event_outbox_replay_log (
            | id, outbox_id, tenant_id, requested_by_user_id, event_type,
            | replay_mode, filter_summary, replayed_at, created_at
            |)
            |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            |""".stripMargin

        val updateStmt = conn.prepareStatement(updateSql)
        val logStmt = conn.prepareStatement(insertLogSql)

        var replayedCount = 0

        events.foreach { event =>
          updateStmt.clearParameters()
          updateStmt.setString(1, TodoOutboxStatus.Pending)
          updateStmt.setTimestamp(2, java.sql.Timestamp.valueOf(replayedAt))
          updateStmt.setTimestamp(3, java.sql.Timestamp.valueOf(replayedAt))
          updateStmt.setString(4, replayedByUserId.toString)
          updateStmt.setString(5, event.id.toString)
          updateStmt.setString(6, event.tenantId.toString)
          updateStmt.setString(7, TodoOutboxStatus.Failed)

          val updatedRows = updateStmt.executeUpdate()

          if (updatedRows > 0) {
            logStmt.clearParameters()
            logStmt.setString(1, UUID.randomUUID().toString)
            logStmt.setString(2, event.id.toString)
            logStmt.setString(3, event.tenantId.toString)
            logStmt.setString(4, replayedByUserId.toString)
            logStmt.setString(5, event.eventType)
            logStmt.setString(6, replayMode)

            filterSummary match {
              case Some(value) => logStmt.setString(7, value.take(1000))
              case None        => logStmt.setNull(7, Types.NVARCHAR)
            }

            logStmt.setTimestamp(8, java.sql.Timestamp.valueOf(replayedAt))
            logStmt.setTimestamp(9, java.sql.Timestamp.valueOf(replayedAt))
            logStmt.executeUpdate()
            replayedCount += 1
          }
        }

        replayedCount
      }
    }
  }

  override def countReplayLogsByTenant(tenantId: UUID): Future[Int] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT COUNT(*) AS total
          |FROM todo_event_outbox_replay_log
          |WHERE tenant_id = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, tenantId.toString)
      val rs = stmt.executeQuery()

      if (rs.next()) rs.getInt("total") else 0
    }
  }

  override def findReplayLogsByTenantPaged(
    tenantId: UUID,
    page: Int,
    pageSize: Int
  ): Future[Seq[TodoOutboxReplayLog]] = Future {
    db.withConnection { conn =>
      val safePage = if (page < 1) 1 else page
      val safePageSize = if (pageSize < 1) 10 else pageSize
      val offset = (safePage - 1) * safePageSize

      val sql =
        """
          |SELECT id, outbox_id, tenant_id, requested_by_user_id, event_type,
          |       replay_mode, filter_summary, replayed_at, created_at
          |FROM todo_event_outbox_replay_log
          |WHERE tenant_id = ?
          |ORDER BY replayed_at DESC
          |OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, tenantId.toString)
      stmt.setInt(2, offset)
      stmt.setInt(3, safePageSize)

      val rs = stmt.executeQuery()
      val items = scala.collection.mutable.ListBuffer[TodoOutboxReplayLog]()

      while (rs.next()) {
        items += mapReplayLog(rs)
      }

      items.toSeq
    }
  }

  def insertOutbox(conn: java.sql.Connection, outboxEvent: TodoOutboxEvent): Unit = {
    val sql =
      """
        |INSERT INTO todo_event_outbox (
        | id, aggregate_type, aggregate_id, event_type, event_version,
        | tenant_id, user_id, payload_json, headers_json, status,
        | attempt_count, available_at, published_at, last_error, created_at,
        | replay_count, last_replayed_at, last_replayed_by_user_id
        |)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
    stmt.setInt(16, outboxEvent.replayCount)

    outboxEvent.lastReplayedAt match {
      case Some(value) => stmt.setTimestamp(17, java.sql.Timestamp.valueOf(value))
      case None        => stmt.setNull(17, Types.TIMESTAMP)
    }

    outboxEvent.lastReplayedByUserId match {
      case Some(value) => stmt.setString(18, value.toString)
      case None        => stmt.setNull(18, Types.NVARCHAR)
    }

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
      createdAt = rs.getTimestamp("created_at").toLocalDateTime,
      replayCount = rs.getInt("replay_count"),
      lastReplayedAt = Option(rs.getTimestamp("last_replayed_at")).map(_.toLocalDateTime),
      lastReplayedByUserId = Option(rs.getString("last_replayed_by_user_id")).map(UUID.fromString)
    )

  private def failedFilterClause(filters: TodoOutboxReplayFilters): String = {
    val eventTypeClause =
      if (filters.normalizedEventType.isDefined) " AND event_type = ?" else ""
    val createdFromClause =
      if (filters.createdFrom.isDefined) " AND created_at >= ?" else ""
    val createdToClause =
      if (filters.createdTo.isDefined) " AND created_at <= ?" else ""

    s"""
       |WHERE status = ? AND tenant_id = ?$eventTypeClause$createdFromClause$createdToClause
       |""".stripMargin
  }

  private def bindFailedFilterParams(
    stmt: java.sql.PreparedStatement,
    tenantId: UUID,
    filters: TodoOutboxReplayFilters
  ): Int = {
    var index = 1
    stmt.setString(index, TodoOutboxStatus.Failed)
    index += 1
    stmt.setString(index, tenantId.toString)
    index += 1

    filters.normalizedEventType.foreach { value =>
      stmt.setString(index, value)
      index += 1
    }

    filters.createdFrom.foreach { value =>
      stmt.setTimestamp(index, java.sql.Timestamp.valueOf(value))
      index += 1
    }

    filters.createdTo.foreach { value =>
      stmt.setTimestamp(index, java.sql.Timestamp.valueOf(value))
      index += 1
    }

    index
  }

  private def mapReplayLog(rs: ResultSet): TodoOutboxReplayLog =
    TodoOutboxReplayLog(
      id = UUID.fromString(rs.getString("id")),
      outboxId = UUID.fromString(rs.getString("outbox_id")),
      tenantId = UUID.fromString(rs.getString("tenant_id")),
      requestedByUserId = UUID.fromString(rs.getString("requested_by_user_id")),
      eventType = rs.getString("event_type"),
      replayMode = rs.getString("replay_mode"),
      filterSummary = Option(rs.getString("filter_summary")),
      replayedAt = rs.getTimestamp("replayed_at").toLocalDateTime,
      createdAt = rs.getTimestamp("created_at").toLocalDateTime
    )
}
