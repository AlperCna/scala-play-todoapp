package repositories

import models.Todo
import play.api.db.Database

import java.sql.{ResultSet, Types}
import java.time.LocalDateTime
import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TodoRepositoryImpl @Inject()(
                                    db: Database
                                  )(implicit ec: ExecutionContext) extends TodoRepository {

  private def mapTodo(rs: ResultSet): Todo = {
    Todo(
      id = UUID.fromString(rs.getString("id")),
      userId = UUID.fromString(rs.getString("user_id")),
      title = rs.getString("title"),
      description = Option(rs.getString("description")),
      isCompleted = rs.getBoolean("is_completed"),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime,
      updatedAt = Option(rs.getTimestamp("updated_at")).map(_.toLocalDateTime),
      deletedAt = Option(rs.getTimestamp("deleted_at")).map(_.toLocalDateTime),
      isDeleted = rs.getBoolean("is_deleted")
    )
  }

  override def findByUserId(userId: UUID): Future[Seq[Todo]] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT id, user_id, title, description, is_completed,
          |       created_at, updated_at, deleted_at, is_deleted
          |FROM todos
          |WHERE user_id = ? AND is_deleted = 0
          |ORDER BY created_at DESC
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, userId.toString)

      val rs = stmt.executeQuery()
      val todos = scala.collection.mutable.ListBuffer[Todo]()

      while (rs.next()) {
        todos += mapTodo(rs)
      }

      todos.toSeq
    }
  }

  override def findByIdAndUserId(id: UUID, userId: UUID): Future[Option[Todo]] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT id, user_id, title, description, is_completed,
          |       created_at, updated_at, deleted_at, is_deleted
          |FROM todos
          |WHERE id = ? AND user_id = ? AND is_deleted = 0
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, id.toString)
      stmt.setString(2, userId.toString)

      val rs = stmt.executeQuery()
      if (rs.next()) Some(mapTodo(rs)) else None
    }
  }

  override def create(todo: Todo): Future[Todo] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |INSERT INTO todos (
          | id, user_id, title, description, is_completed,
          | created_at, updated_at, deleted_at, is_deleted
          |)
          |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)

      stmt.setString(1, todo.id.toString)
      stmt.setString(2, todo.userId.toString)
      stmt.setString(3, todo.title)

      todo.description match {
        case Some(value) => stmt.setString(4, value)
        case None => stmt.setNull(4, Types.NVARCHAR)
      }

      stmt.setBoolean(5, todo.isCompleted)
      stmt.setTimestamp(6, java.sql.Timestamp.valueOf(todo.createdAt))

      todo.updatedAt match {
        case Some(value) => stmt.setTimestamp(7, java.sql.Timestamp.valueOf(value))
        case None => stmt.setNull(7, Types.TIMESTAMP)
      }

      todo.deletedAt match {
        case Some(value) => stmt.setTimestamp(8, java.sql.Timestamp.valueOf(value))
        case None => stmt.setNull(8, Types.TIMESTAMP)
      }

      stmt.setBoolean(9, todo.isDeleted)

      stmt.executeUpdate()
      todo
    }
  }

  override def update(todo: Todo): Future[Todo] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |UPDATE todos
          |SET title = ?,
          |    description = ?,
          |    is_completed = ?,
          |    updated_at = ?
          |WHERE id = ? AND user_id = ? AND is_deleted = 0
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)

      stmt.setString(1, todo.title)

      todo.description match {
        case Some(value) => stmt.setString(2, value)
        case None => stmt.setNull(2, Types.NVARCHAR)
      }

      stmt.setBoolean(3, todo.isCompleted)
      stmt.setTimestamp(4, java.sql.Timestamp.valueOf(LocalDateTime.now()))
      stmt.setString(5, todo.id.toString)
      stmt.setString(6, todo.userId.toString)

      stmt.executeUpdate()
      todo.copy(updatedAt = Some(LocalDateTime.now()))
    }
  }

  override def delete(id: UUID, userId: UUID): Future[Boolean] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |UPDATE todos
          |SET is_deleted = 1,
          |    deleted_at = ?
          |WHERE id = ? AND user_id = ? AND is_deleted = 0
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setTimestamp(1, java.sql.Timestamp.valueOf(LocalDateTime.now()))
      stmt.setString(2, id.toString)
      stmt.setString(3, userId.toString)

      stmt.executeUpdate() > 0
    }
  }

  override def findByUserIdWithStatus(userId: UUID, status: String): Future[Seq[Todo]] = Future {
    db.withConnection { conn =>

      val statusCondition = status match {
        case "active" => "AND is_completed = 0"
        case "completed" => "AND is_completed = 1"
        case _ => ""
      }

      val sql =
        s"""
           |SELECT id, user_id, title, description, is_completed,
           |       created_at, updated_at, deleted_at, is_deleted
           |FROM todos
           |WHERE user_id = ? AND is_deleted = 0
           |$statusCondition
           |ORDER BY created_at DESC
           |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, userId.toString)

      val rs = stmt.executeQuery()
      val todos = scala.collection.mutable.ListBuffer[Todo]()

      while (rs.next()) {
        todos += mapTodo(rs)
      }

      todos.toSeq
    }
  }


  override def findByUserIdWithFilters(
                                        userId: UUID,
                                        status: String,
                                        search: String
                                      ): Future[Seq[Todo]] = Future {
    db.withConnection { conn =>

      val statusCondition = status match {
        case "active" => "AND is_completed = 0"
        case "completed" => "AND is_completed = 1"
        case _ => ""
      }

      val hasSearch = search.trim.nonEmpty

      val searchCondition =
        if (hasSearch) {
          "AND (title LIKE ? OR description LIKE ?)"
        } else {
          ""
        }

      val sql =
        s"""
           |SELECT id, user_id, title, description, is_completed,
           |       created_at, updated_at, deleted_at, is_deleted
           |FROM todos
           |WHERE user_id = ? AND is_deleted = 0
           |$statusCondition
           |$searchCondition
           |ORDER BY created_at DESC
           |""".stripMargin

      val stmt = conn.prepareStatement(sql)

      stmt.setString(1, userId.toString)

      if (hasSearch) {
        val keyword = s"%${search.trim}%"
        stmt.setString(2, keyword)
        stmt.setString(3, keyword)
      }

      val rs = stmt.executeQuery()
      val todos = scala.collection.mutable.ListBuffer[Todo]()

      while (rs.next()) {
        todos += mapTodo(rs)
      }

      todos.toSeq
    }
  }

  override def findByUserIdWithFiltersPaged(
                                             userId: UUID,
                                             status: String,
                                             search: String,
                                             page: Int,
                                             pageSize: Int
                                           ): Future[Seq[Todo]] = Future {
    db.withConnection { conn =>

      val statusCondition = status match {
        case "active" => "AND is_completed = 0"
        case "completed" => "AND is_completed = 1"
        case _ => ""
      }

      val hasSearch = search.trim.nonEmpty

      val searchCondition =
        if (hasSearch) {
          "AND (title LIKE ? OR description LIKE ?)"
        } else {
          ""
        }

      val offset = (page - 1) * pageSize

      val sql =
        s"""
           |SELECT id, user_id, title, description, is_completed,
           |       created_at, updated_at, deleted_at, is_deleted
           |FROM todos
           |WHERE user_id = ? AND is_deleted = 0
           |$statusCondition
           |$searchCondition
           |ORDER BY created_at DESC
           |OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
           |""".stripMargin

      val stmt = conn.prepareStatement(sql)

      stmt.setString(1, userId.toString)

      if (hasSearch) {
        val keyword = s"%${search.trim}%"
        stmt.setString(2, keyword)
        stmt.setString(3, keyword)
        stmt.setInt(4, offset)
        stmt.setInt(5, pageSize)
      } else {
        stmt.setInt(2, offset)
        stmt.setInt(3, pageSize)
      }

      val rs = stmt.executeQuery()
      val todos = scala.collection.mutable.ListBuffer[Todo]()

      while (rs.next()) {
        todos += mapTodo(rs)
      }

      todos.toSeq
    }
  }

  override def countByUserIdWithFilters(
                                         userId: UUID,
                                         status: String,
                                         search: String
                                       ): Future[Int] = Future {
    db.withConnection { conn =>

      val statusCondition = status match {
        case "active" => "AND is_completed = 0"
        case "completed" => "AND is_completed = 1"
        case _ => ""
      }

      val hasSearch = search.trim.nonEmpty

      val searchCondition =
        if (hasSearch) {
          "AND (title LIKE ? OR description LIKE ?)"
        } else {
          ""
        }

      val sql =
        s"""
           |SELECT COUNT(*) AS total
           |FROM todos
           |WHERE user_id = ? AND is_deleted = 0
           |$statusCondition
           |$searchCondition
           |""".stripMargin

      val stmt = conn.prepareStatement(sql)

      stmt.setString(1, userId.toString)

      if (hasSearch) {
        val keyword = s"%${search.trim}%"
        stmt.setString(2, keyword)
        stmt.setString(3, keyword)
      }

      val rs = stmt.executeQuery()

      if (rs.next()) rs.getInt("total") else 0
    }
  }
}