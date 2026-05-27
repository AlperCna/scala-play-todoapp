package com.alper.todo.analyticsconsumer.infrastructure

import com.alper.todo.analyticsconsumer.config.AnalyticsConsumerDatabaseSettings
import com.alper.todo.analyticsconsumer.model.AnalyticsProcessingResult.{DuplicateIgnored, Processed}
import com.alper.todo.analyticsconsumer.model.{AnalyticsCommand, AnalyticsProcessingResult}
import com.alper.todo.analyticsconsumer.ports.AnalyticsProjectionWriter

import java.math.RoundingMode
import java.sql.{Connection, Date, DriverManager, PreparedStatement, Timestamp}
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class JdbcAnalyticsProjectionWriter(
  databaseSettings: AnalyticsConsumerDatabaseSettings,
  consumerName: String
)(implicit ec: ExecutionContext) extends AnalyticsProjectionWriter {

  Class.forName(databaseSettings.driver)

  override def writeIfNew(command: AnalyticsCommand): Future[AnalyticsProcessingResult] = Future {
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
        upsertProjection(conn, command)
        upsertDailyMetrics(conn, command)
        refreshSummary(conn, command.tenantId, command.occurredAt)
        insertProcessedEvent(conn, command.eventId, command.tenantId, command.occurredAt)
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

  private def upsertProjection(conn: Connection, command: AnalyticsCommand): Unit = {
    if (projectionExists(conn, command.todoId)) {
      updateProjection(conn, command)
    } else {
      insertProjection(conn, command)
    }
  }

  private def projectionExists(conn: Connection, todoId: UUID): Boolean = {
    val sql =
      """
        |SELECT COUNT(*) AS total
        |FROM tenant_todo_analytics_projection
        |WHERE todo_id = ?
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setString(1, todoId.toString)

    val rs = stmt.executeQuery()
    if (rs.next()) rs.getInt("total") > 0 else false
  }

  private def insertProjection(conn: Connection, command: AnalyticsCommand): Unit = {
    val sql =
      """
        |INSERT INTO tenant_todo_analytics_projection (
        |  todo_id, tenant_id, user_id, title, description, due_date,
        |  is_completed, is_deleted, created_at, updated_at, completed_at,
        |  deleted_at, last_event_type, last_event_at
        |)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setString(1, command.todoId.toString)
    stmt.setString(2, command.tenantId.toString)
    stmt.setString(3, command.userId.toString)
    stmt.setString(4, command.title)
    setOptionalString(stmt, 5, command.description)
    setOptionalTimestamp(stmt, 6, command.dueDate)
    stmt.setBoolean(7, isCompletedFor(command))
    stmt.setBoolean(8, isDeletedFor(command))
    stmt.setTimestamp(9, Timestamp.valueOf(command.createdAt))
    setOptionalTimestamp(stmt, 10, command.updatedAt)
    setOptionalTimestamp(stmt, 11, completedAtFor(command))
    setOptionalTimestamp(stmt, 12, deletedAtFor(command))
    stmt.setString(13, command.eventType)
    stmt.setTimestamp(14, Timestamp.valueOf(command.occurredAt))
    stmt.executeUpdate()
  }

  private def updateProjection(conn: Connection, command: AnalyticsCommand): Unit = {
    val sql =
      """
        |UPDATE tenant_todo_analytics_projection
        |SET tenant_id = ?,
        |    user_id = ?,
        |    title = ?,
        |    description = ?,
        |    due_date = ?,
        |    is_completed = ?,
        |    is_deleted = ?,
        |    updated_at = ?,
        |    completed_at = ?,
        |    deleted_at = ?,
        |    last_event_type = ?,
        |    last_event_at = ?
        |WHERE todo_id = ?
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setString(1, command.tenantId.toString)
    stmt.setString(2, command.userId.toString)
    stmt.setString(3, command.title)
    setOptionalString(stmt, 4, command.description)
    setOptionalTimestamp(stmt, 5, command.dueDate)
    stmt.setBoolean(6, isCompletedFor(command))
    stmt.setBoolean(7, isDeletedFor(command))
    setOptionalTimestamp(stmt, 8, command.updatedAt.orElse(Some(command.occurredAt)))
    setOptionalTimestamp(stmt, 9, completedAtFor(command))
    setOptionalTimestamp(stmt, 10, deletedAtFor(command))
    stmt.setString(11, command.eventType)
    stmt.setTimestamp(12, Timestamp.valueOf(command.occurredAt))
    stmt.setString(13, command.todoId.toString)
    stmt.executeUpdate()
  }

  private def upsertDailyMetrics(conn: Connection, command: AnalyticsCommand): Unit = {
    val metricDate = command.occurredAt.toLocalDate
    if (dailyMetricsExists(conn, command.tenantId, metricDate)) {
      incrementDailyMetrics(conn, command, metricDate)
    } else {
      insertDailyMetrics(conn, command, metricDate)
    }
  }

  private def dailyMetricsExists(conn: Connection, tenantId: UUID, metricDate: LocalDate): Boolean = {
    val sql =
      """
        |SELECT COUNT(*) AS total
        |FROM tenant_todo_daily_metrics
        |WHERE tenant_id = ? AND metric_date = ?
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setString(1, tenantId.toString)
    stmt.setDate(2, Date.valueOf(metricDate))

    val rs = stmt.executeQuery()
    if (rs.next()) rs.getInt("total") > 0 else false
  }

  private def insertDailyMetrics(conn: Connection, command: AnalyticsCommand, metricDate: LocalDate): Unit = {
    val sql =
      """
        |INSERT INTO tenant_todo_daily_metrics (
        |  tenant_id, metric_date, created_count, updated_count,
        |  completed_count, deleted_count, updated_at
        |)
        |VALUES (?, ?, ?, ?, ?, ?, ?)
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setString(1, command.tenantId.toString)
    stmt.setDate(2, Date.valueOf(metricDate))
    stmt.setInt(3, eventIncrement(command.eventType, "TodoCreated"))
    stmt.setInt(4, eventIncrement(command.eventType, "TodoUpdated"))
    stmt.setInt(5, eventIncrement(command.eventType, "TodoCompleted"))
    stmt.setInt(6, eventIncrement(command.eventType, "TodoDeleted"))
    stmt.setTimestamp(7, Timestamp.valueOf(command.occurredAt))
    stmt.executeUpdate()
  }

  private def incrementDailyMetrics(conn: Connection, command: AnalyticsCommand, metricDate: LocalDate): Unit = {
    val sql =
      """
        |UPDATE tenant_todo_daily_metrics
        |SET created_count = created_count + ?,
        |    updated_count = updated_count + ?,
        |    completed_count = completed_count + ?,
        |    deleted_count = deleted_count + ?,
        |    updated_at = ?
        |WHERE tenant_id = ? AND metric_date = ?
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setInt(1, eventIncrement(command.eventType, "TodoCreated"))
    stmt.setInt(2, eventIncrement(command.eventType, "TodoUpdated"))
    stmt.setInt(3, eventIncrement(command.eventType, "TodoCompleted"))
    stmt.setInt(4, eventIncrement(command.eventType, "TodoDeleted"))
    stmt.setTimestamp(5, Timestamp.valueOf(command.occurredAt))
    stmt.setString(6, command.tenantId.toString)
    stmt.setDate(7, Date.valueOf(metricDate))
    stmt.executeUpdate()
  }

  private def refreshSummary(conn: Connection, tenantId: UUID, occurredAt: LocalDateTime): Unit = {
    val state = summarizeProjectionState(conn, tenantId)
    val eventTotals = summarizeEventTotals(conn, tenantId)
    val completionRate: BigDecimal =
      if (state.activeTodos == 0) BigDecimal(0)
      else BigDecimal(
        BigDecimal(state.completedTodos).bigDecimal
          .divide(BigDecimal(state.activeTodos).bigDecimal, 4, RoundingMode.HALF_UP)
      )

    if (summaryExists(conn, tenantId)) {
      updateSummary(conn, tenantId, state, eventTotals, completionRate, occurredAt)
    } else {
      insertSummary(conn, tenantId, state, eventTotals, completionRate, occurredAt)
    }
  }

  private def summarizeProjectionState(conn: Connection, tenantId: UUID): ProjectionState = {
    val sql =
      """
        |SELECT
        |  COUNT(*) AS total_tracked,
        |  SUM(CASE WHEN is_deleted = 0 THEN 1 ELSE 0 END) AS active_total,
        |  SUM(CASE WHEN is_deleted = 0 AND is_completed = 1 THEN 1 ELSE 0 END) AS completed_total,
        |  SUM(CASE WHEN is_deleted = 0 AND is_completed = 0 THEN 1 ELSE 0 END) AS open_total,
        |  SUM(CASE WHEN is_deleted = 1 THEN 1 ELSE 0 END) AS deleted_total
        |FROM tenant_todo_analytics_projection
        |WHERE tenant_id = ?
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setString(1, tenantId.toString)

    val rs = stmt.executeQuery()
    rs.next()

    ProjectionState(
      totalTrackedTodos = rs.getInt("total_tracked"),
      activeTodos = rs.getInt("active_total"),
      completedTodos = rs.getInt("completed_total"),
      openTodos = rs.getInt("open_total"),
      deletedTodos = rs.getInt("deleted_total")
    )
  }

  private def summarizeEventTotals(conn: Connection, tenantId: UUID): EventTotals = {
    val sql =
      """
        |SELECT
        |  COALESCE(SUM(created_count), 0) AS created_total,
        |  COALESCE(SUM(updated_count), 0) AS updated_total,
        |  COALESCE(SUM(completed_count), 0) AS completed_total,
        |  COALESCE(SUM(deleted_count), 0) AS deleted_total
        |FROM tenant_todo_daily_metrics
        |WHERE tenant_id = ?
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setString(1, tenantId.toString)

    val rs = stmt.executeQuery()
    rs.next()

    EventTotals(
      createdEvents = rs.getInt("created_total"),
      updatedEvents = rs.getInt("updated_total"),
      completedEvents = rs.getInt("completed_total"),
      deletedEvents = rs.getInt("deleted_total")
    )
  }

  private def summaryExists(conn: Connection, tenantId: UUID): Boolean = {
    val sql =
      """
        |SELECT COUNT(*) AS total
        |FROM tenant_todo_analytics_summary
        |WHERE tenant_id = ?
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setString(1, tenantId.toString)

    val rs = stmt.executeQuery()
    if (rs.next()) rs.getInt("total") > 0 else false
  }

  private def insertSummary(
    conn: Connection,
    tenantId: UUID,
    state: ProjectionState,
    totals: EventTotals,
    completionRate: BigDecimal,
    occurredAt: LocalDateTime
  ): Unit = {
    val sql =
      """
        |INSERT INTO tenant_todo_analytics_summary (
        |  tenant_id, total_tracked_todos, active_todos, completed_todos,
        |  open_todos, deleted_todos, created_events, updated_events,
        |  completed_events, deleted_events, completion_rate, last_event_at, updated_at
        |)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    fillSummaryStatement(stmt, tenantId, state, totals, completionRate, occurredAt)
    stmt.executeUpdate()
  }

  private def updateSummary(
    conn: Connection,
    tenantId: UUID,
    state: ProjectionState,
    totals: EventTotals,
    completionRate: BigDecimal,
    occurredAt: LocalDateTime
  ): Unit = {
    val sql =
      """
        |UPDATE tenant_todo_analytics_summary
        |SET total_tracked_todos = ?,
        |    active_todos = ?,
        |    completed_todos = ?,
        |    open_todos = ?,
        |    deleted_todos = ?,
        |    created_events = ?,
        |    updated_events = ?,
        |    completed_events = ?,
        |    deleted_events = ?,
        |    completion_rate = ?,
        |    last_event_at = ?,
        |    updated_at = ?
        |WHERE tenant_id = ?
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setInt(1, state.totalTrackedTodos)
    stmt.setInt(2, state.activeTodos)
    stmt.setInt(3, state.completedTodos)
    stmt.setInt(4, state.openTodos)
    stmt.setInt(5, state.deletedTodos)
    stmt.setInt(6, totals.createdEvents)
    stmt.setInt(7, totals.updatedEvents)
    stmt.setInt(8, totals.completedEvents)
    stmt.setInt(9, totals.deletedEvents)
    stmt.setBigDecimal(10, completionRate.bigDecimal)
    stmt.setTimestamp(11, Timestamp.valueOf(occurredAt))
    stmt.setTimestamp(12, Timestamp.valueOf(occurredAt))
    stmt.setString(13, tenantId.toString)
    stmt.executeUpdate()
  }

  private def fillSummaryStatement(
    stmt: PreparedStatement,
    tenantId: UUID,
    state: ProjectionState,
    totals: EventTotals,
    completionRate: BigDecimal,
    occurredAt: LocalDateTime
  ): Unit = {
    stmt.setString(1, tenantId.toString)
    stmt.setInt(2, state.totalTrackedTodos)
    stmt.setInt(3, state.activeTodos)
    stmt.setInt(4, state.completedTodos)
    stmt.setInt(5, state.openTodos)
    stmt.setInt(6, state.deletedTodos)
    stmt.setInt(7, totals.createdEvents)
    stmt.setInt(8, totals.updatedEvents)
    stmt.setInt(9, totals.completedEvents)
    stmt.setInt(10, totals.deletedEvents)
    stmt.setBigDecimal(11, completionRate.bigDecimal)
    stmt.setTimestamp(12, Timestamp.valueOf(occurredAt))
    stmt.setTimestamp(13, Timestamp.valueOf(occurredAt))
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
        |  consumer_name, event_id, tenant_id, processed_at
        |)
        |VALUES (?, ?, ?, ?)
        |""".stripMargin

    val stmt = conn.prepareStatement(sql)
    stmt.setString(1, consumerName)
    stmt.setString(2, eventId.toString)
    stmt.setString(3, tenantId.toString)
    stmt.setTimestamp(4, Timestamp.valueOf(processedAt))
    stmt.executeUpdate()
  }

  private def isCompletedFor(command: AnalyticsCommand): Boolean =
    command.eventType match {
      case "TodoCompleted" => true
      case "TodoDeleted"   => command.isCompleted
      case _               => command.isCompleted
    }

  private def isDeletedFor(command: AnalyticsCommand): Boolean =
    command.eventType == "TodoDeleted"

  private def completedAtFor(command: AnalyticsCommand): Option[LocalDateTime] =
    command.eventType match {
      case "TodoCompleted" => Some(command.occurredAt)
      case _ if command.isCompleted => command.updatedAt.orElse(Some(command.occurredAt))
      case _ => None
    }

  private def deletedAtFor(command: AnalyticsCommand): Option[LocalDateTime] =
    if (command.eventType == "TodoDeleted") command.deletedAt.orElse(Some(command.occurredAt)) else None

  private def setOptionalString(stmt: PreparedStatement, index: Int, value: Option[String]): Unit =
    value match {
      case Some(v) => stmt.setString(index, v)
      case None    => stmt.setNull(index, java.sql.Types.NVARCHAR)
    }

  private def setOptionalTimestamp(stmt: PreparedStatement, index: Int, value: Option[LocalDateTime]): Unit =
    value match {
      case Some(v) => stmt.setTimestamp(index, Timestamp.valueOf(v))
      case None    => stmt.setNull(index, java.sql.Types.TIMESTAMP)
    }

  private def eventIncrement(actualEventType: String, expectedEventType: String): Int =
    if (actualEventType == expectedEventType) 1 else 0

  private case class ProjectionState(
    totalTrackedTodos: Int,
    activeTodos: Int,
    completedTodos: Int,
    openTodos: Int,
    deletedTodos: Int
  )

  private case class EventTotals(
    createdEvents: Int,
    updatedEvents: Int,
    completedEvents: Int,
    deletedEvents: Int
  )
}
