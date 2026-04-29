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
      isDeleted = rs.getBoolean("is_deleted"),
      tenantId = UUID.fromString(rs.getString("tenant_id"))
    )
  }

  override def findByUserId(userId: UUID): Future[Seq[Todo]] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT id, user_id, title, description, is_completed,
          |       created_at, updated_at, deleted_at, is_deleted, tenant_id
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
          |       created_at, updated_at, deleted_at, is_deleted, tenant_id
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
          | created_at, updated_at, deleted_at, is_deleted, tenant_id
          |)
          |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
      stmt.setString(10, todo.tenantId.toString)

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
           |       created_at, updated_at, deleted_at, is_deleted, tenant_id
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
           |       created_at, updated_at, deleted_at, is_deleted, tenant_id
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
           |       created_at, updated_at, deleted_at, is_deleted, tenant_id
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

  override def countAllTodos(tenantId: UUID): Future[Int] = Future {
    db.withConnection { conn =>
      val sql =
        """
          |SELECT COUNT(*) AS total
          |FROM todos
          |WHERE is_deleted = 0 AND tenant_id = ?
          |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, tenantId.toString)
      val rs = stmt.executeQuery()

      if (rs.next()) rs.getInt("total") else 0
    }
  }

  override def countAllTodosWithFilters(tenantId: UUID, status: String, search: String): Future[Int] = Future {
    db.withConnection { conn =>
      val statusCondition = status match {
        case "active" => "AND t.is_completed = 0"
        case "completed" => "AND t.is_completed = 1"
        case _ => ""
      }

      val hasSearch = search.trim.nonEmpty

      val searchCondition =
        if (hasSearch) {
          "AND (t.title LIKE ? OR t.description LIKE ? OR u.username LIKE ? OR u.email LIKE ?)"
        } else {
          ""
        }

      val sql =
        s"""
           |SELECT COUNT(*) AS total
           |FROM todos t
           |INNER JOIN users u ON t.user_id = u.id
           |WHERE t.is_deleted = 0 AND t.tenant_id = ?
           |$statusCondition
           |$searchCondition
           |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, tenantId.toString)

      if (hasSearch) {
        val keyword = s"%${search.trim}%"
        stmt.setString(2, keyword)
        stmt.setString(3, keyword)
        stmt.setString(4, keyword)
        stmt.setString(5, keyword)
      }

      val rs = stmt.executeQuery()
      if (rs.next()) rs.getInt("total") else 0
    }
  }

  override def findAllTodosWithUserPaged(
                                          tenantId: UUID,
                                          status: String,
                                          search: String,
                                          page: Int,
                                          pageSize: Int
                                        ): Future[Seq[(Todo, String, String)]] = Future {
    db.withConnection { conn =>
      val statusCondition = status match {
        case "active" => "AND t.is_completed = 0"
        case "completed" => "AND t.is_completed = 1"
        case _ => ""
      }

      val hasSearch = search.trim.nonEmpty

      val searchCondition =
        if (hasSearch) {
          "AND (t.title LIKE ? OR t.description LIKE ? OR u.username LIKE ? OR u.email LIKE ?)"
        } else {
          ""
        }

      val safePage = if (page < 1) 1 else page
      val safePageSize = if (pageSize < 1) 10 else pageSize
      val offset = (safePage - 1) * safePageSize

      val sql =
        s"""
           |SELECT
           |  t.id, t.user_id, t.title, t.description, t.is_completed,
           |  t.created_at, t.updated_at, t.deleted_at, t.is_deleted, t.tenant_id,
           |  u.username, u.email
           |FROM todos t
           |INNER JOIN users u ON t.user_id = u.id
           |WHERE t.is_deleted = 0 AND t.tenant_id = ?
           |$statusCondition
           |$searchCondition
           |ORDER BY t.created_at DESC
           |OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
           |""".stripMargin

      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, tenantId.toString)

      if (hasSearch) {
        val keyword = s"%${search.trim}%"
        stmt.setString(2, keyword)
        stmt.setString(3, keyword)
        stmt.setString(4, keyword)
        stmt.setString(5, keyword)
        stmt.setInt(6, offset)
        stmt.setInt(7, safePageSize)
      } else {
        stmt.setInt(2, offset)
        stmt.setInt(3, safePageSize)
      }

      val rs = stmt.executeQuery()
      val todos = scala.collection.mutable.ListBuffer[(Todo, String, String)]()

      while (rs.next()) {
        todos += ((mapTodo(rs), rs.getString("username"), rs.getString("email")))
      }

      todos.toSeq
    }
  }
}