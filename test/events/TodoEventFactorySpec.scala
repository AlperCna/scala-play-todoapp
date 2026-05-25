package events

import models.Todo
import org.scalatestplus.play.PlaySpec

import java.time.LocalDateTime
import java.util.UUID

class TodoEventFactorySpec extends PlaySpec {

  private val factory = new TodoEventFactory()

  private def sampleTodo(isCompleted: Boolean = false): Todo =
    Todo(
      id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
      userId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
      title = "Kafka rollout",
      description = Some("Prepare phase 1"),
      isCompleted = isCompleted,
      createdAt = LocalDateTime.of(2026, 5, 25, 10, 0),
      updatedAt = Some(LocalDateTime.of(2026, 5, 25, 11, 0)),
      deletedAt = None,
      isDeleted = false,
      tenantId = UUID.fromString("33333333-3333-3333-3333-333333333333"),
      dueDate = Some(LocalDateTime.of(2026, 5, 30, 9, 0))
    )

  "TodoEventFactory" should {

    "build a TodoCreated envelope with the shared contract fields" in {
      val todo = sampleTodo()

      val event = factory.todoCreated(todo, Some("req-123"))

      event.eventType mustBe "TodoCreated"
      event.eventVersion mustBe 1
      event.tenantId mustBe todo.tenantId
      event.userId mustBe todo.userId
      event.entityType mustBe "todo"
      event.entityId mustBe todo.id
      event.correlationId mustBe Some("req-123")
      (event.payload \ "todoId").as[String] mustBe todo.id.toString
      (event.payload \ "title").as[String] mustBe todo.title
      (event.payload \ "isCompleted").as[Boolean] mustBe false
    }

    "build a TodoCompleted envelope from the todo state that was saved" in {
      val completedTodo = sampleTodo(isCompleted = true)

      val event = factory.todoCompleted(completedTodo)

      event.eventType mustBe "TodoCompleted"
      (event.payload \ "isCompleted").as[Boolean] mustBe true
      (event.payload \ "tenantId").as[String] mustBe completedTodo.tenantId.toString
      (event.payload \ "userId").as[String] mustBe completedTodo.userId.toString
    }
  }
}
