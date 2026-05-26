package kafka.outbox

import models.Todo
import play.api.db.DBApi

import java.sql.Types
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TodoOutboxCommandRepositoryImpl @Inject()(
  dbApi: DBApi,
  todoOutboxRepository: TodoOutboxRepositoryImpl
)(implicit ec: ExecutionContext) extends TodoOutboxCommandRepository {

  private val db = dbApi.database("default")

  override def createTodoWithOutbox(todo: Todo, outboxEvent: TodoOutboxEvent): Future[Todo] = Future {
    db.withTransaction { conn =>
      val sql =
        """
          |INSERT INTO todos (
          | id, user_id, title, description, is_completed,
          | created_at, updated_at, deleted_at, is_deleted, tenant_id, due_date
          |)
          |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, todo.id.toString)
      stmt.setString(2, todo.userId.toString)
      stmt.setString(3, todo.title)
      setNullableString(stmt, 4, todo.description)
      stmt.setBoolean(5, todo.isCompleted)
      stmt.setTimestamp(6, java.sql.Timestamp.valueOf(todo.createdAt))
      setNullableDateTime(stmt, 7, todo.updatedAt)
      setNullableDateTime(stmt, 8, todo.deletedAt)
      stmt.setBoolean(9, todo.isDeleted)
      stmt.setString(10, todo.tenantId.toString)
      setNullableDateTime(stmt, 11, todo.dueDate)
      stmt.executeUpdate()

      todoOutboxRepository.insertOutbox(conn, outboxEvent)
      todo
    }
  }

  override def updateTodoWithOutbox(todo: Todo, outboxEvent: Option[TodoOutboxEvent]): Future[Todo] = Future {
    db.withTransaction { conn =>
      val sql =
        """
          |UPDATE todos
          |SET title = ?,
          |    description = ?,
          |    is_completed = ?,
          |    updated_at = ?,
          |    due_date = ?
          |WHERE id = ? AND user_id = ? AND is_deleted = 0
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, todo.title)
      setNullableString(stmt, 2, todo.description)
      stmt.setBoolean(3, todo.isCompleted)
      stmt.setTimestamp(4, java.sql.Timestamp.valueOf(todo.updatedAt.getOrElse(LocalDateTime.now())))
      setNullableDateTime(stmt, 5, todo.dueDate)
      stmt.setString(6, todo.id.toString)
      stmt.setString(7, todo.userId.toString)

      val updatedRows = stmt.executeUpdate()
      if (updatedRows == 0) {
        throw new IllegalStateException(s"Todo update failed for ${todo.id}")
      }

      outboxEvent.foreach(todoOutboxRepository.insertOutbox(conn, _))
      todo
    }
  }

  override def deleteTodoWithOutbox(
    todoId: UUID,
    userId: UUID,
    deletedAt: LocalDateTime,
    outboxEvent: TodoOutboxEvent
  ): Future[Boolean] = Future {
    db.withTransaction { conn =>
      val sql =
        """
          |UPDATE todos
          |SET is_deleted = 1,
          |    deleted_at = ?
          |WHERE id = ? AND user_id = ? AND is_deleted = 0
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setTimestamp(1, java.sql.Timestamp.valueOf(deletedAt))
      stmt.setString(2, todoId.toString)
      stmt.setString(3, userId.toString)

      val deleted = stmt.executeUpdate() > 0
      if (deleted) {
        todoOutboxRepository.insertOutbox(conn, outboxEvent)
      }

      deleted
    }
  }

  private def setNullableString(stmt: java.sql.PreparedStatement, index: Int, value: Option[String]): Unit =
    value match {
      case Some(v) => stmt.setString(index, v)
      case None    => stmt.setNull(index, Types.NVARCHAR)
    }

  private def setNullableDateTime(
    stmt: java.sql.PreparedStatement,
    index: Int,
    value: Option[LocalDateTime]
  ): Unit =
    value match {
      case Some(v) => stmt.setTimestamp(index, java.sql.Timestamp.valueOf(v))
      case None    => stmt.setNull(index, Types.TIMESTAMP)
    }
}
