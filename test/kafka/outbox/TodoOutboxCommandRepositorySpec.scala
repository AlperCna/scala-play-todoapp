package kafka.outbox

import kafka.events.TodoEventFactory
import models.{Tenant, Todo, User}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.{TenantRepository, TodoRepository, UserRepository}

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.duration._

class TodoOutboxCommandRepositorySpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with ScalaFutures {

  override def fakeApplication() =
    new GuiceApplicationBuilder().build()

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 10.seconds, interval = 200.millis)

  private lazy val commandRepository = app.injector.instanceOf[TodoOutboxCommandRepository]
  private lazy val outboxRepository = app.injector.instanceOf[TodoOutboxRepository]
  private lazy val outboxFactory = app.injector.instanceOf[TodoOutboxEventFactory]
  private lazy val eventFactory = app.injector.instanceOf[TodoEventFactory]
  private lazy val tenantRepository = app.injector.instanceOf[TenantRepository]
  private lazy val userRepository = app.injector.instanceOf[UserRepository]
  private lazy val todoRepository = app.injector.instanceOf[TodoRepository]
  private lazy val database = app.injector.instanceOf[Database]

  "TodoOutboxCommandRepository" should {

    "persist a created todo and its outbox event in the same flow" in {
      val fixture = createFixture()
      val todo = sampleTodo(fixture.user.id, fixture.tenant.id)
      val outboxEvent = outboxFactory.fromEnvelope(eventFactory.todoCreated(todo, Some("phase2-create")))

      try {
        whenReady(commandRepository.createTodoWithOutbox(todo, outboxEvent)) { savedTodo =>
          savedTodo.id mustBe todo.id
        }

        whenReady(todoRepository.findByIdAndUserId(todo.id, fixture.user.id)) { maybeTodo =>
          maybeTodo.map(_.title) mustBe Some(todo.title)
        }

        whenReady(outboxRepository.findByAggregateId(todo.id)) { outboxItems =>
          outboxItems.size mustBe 1
          outboxItems.head.eventType mustBe "TodoCreated"
          outboxItems.head.status mustBe TodoOutboxStatus.Pending
          outboxItems.head.payloadJson must include(todo.title)
        }
      } finally {
        cleanupFixture(fixture, Some(todo.id))
      }
    }

    "persist an updated todo and append a new outbox event" in {
      val fixture = createFixture()
      val todo = sampleTodo(fixture.user.id, fixture.tenant.id)
      val createOutbox = outboxFactory.fromEnvelope(eventFactory.todoCreated(todo))

      try {
        whenReady(commandRepository.createTodoWithOutbox(todo, createOutbox))(_ => succeed)

        val updatedTodo = todo.copy(
          title = "Phase 2 updated todo",
          updatedAt = Some(LocalDateTime.now().plusMinutes(5))
        )
        val updateOutbox = outboxFactory.fromEnvelope(eventFactory.todoUpdated(updatedTodo))

        whenReady(commandRepository.updateTodoWithOutbox(updatedTodo, Some(updateOutbox))) { savedTodo =>
          savedTodo.title mustBe "Phase 2 updated todo"
        }

        whenReady(todoRepository.findByIdAndUserId(todo.id, fixture.user.id)) { maybeTodo =>
          maybeTodo.map(_.title) mustBe Some("Phase 2 updated todo")
        }

        whenReady(outboxRepository.findByAggregateId(todo.id)) { outboxItems =>
          outboxItems.map(_.eventType).toSet mustBe Set("TodoCreated", "TodoUpdated")
          outboxItems.size mustBe 2
        }
      } finally {
        cleanupFixture(fixture, Some(todo.id))
      }
    }

    "roll back the todo insert when the outbox insert fails" in {
      val fixture = createFixture()
      val todo = sampleTodo(fixture.user.id, fixture.tenant.id)
      val invalidOutbox = outboxFactory
        .fromEnvelope(eventFactory.todoCreated(todo))
        .copy(eventType = "X" * 101)

      try {
        whenReady(commandRepository.createTodoWithOutbox(todo, invalidOutbox).failed) { _ =>
          succeed
        }

        whenReady(todoRepository.findByIdAndUserId(todo.id, fixture.user.id)) { maybeTodo =>
          maybeTodo mustBe None
        }

        whenReady(outboxRepository.findByAggregateId(todo.id)) { outboxItems =>
          outboxItems mustBe empty
        }
      } finally {
        cleanupFixture(fixture, Some(todo.id))
      }
    }
  }

  private case class Fixture(tenant: Tenant, user: User)

  private def createFixture(): Fixture = {
    val unique = UUID.randomUUID().toString.take(8)
    val tenant = Tenant(
      id = UUID.randomUUID(),
      name = s"Kafka Test $unique",
      domain = s"phase2-$unique.test",
      createdAt = LocalDateTime.now(),
      updatedAt = None
    )

    val user = User(
      id = UUID.randomUUID(),
      username = s"user_$unique",
      email = s"phase2_$unique@example.com",
      passwordHash = "hash",
      role = "USER",
      createdAt = LocalDateTime.now(),
      updatedAt = None,
      isActive = true,
      tenantId = tenant.id
    )

    whenReady(tenantRepository.create(tenant))(_ => succeed)
    whenReady(userRepository.create(user))(_ => succeed)

    Fixture(tenant, user)
  }

  private def sampleTodo(userId: UUID, tenantId: UUID): Todo =
    Todo(
      id = UUID.randomUUID(),
      userId = userId,
      title = "Kafka phase 2 todo",
      description = Some("Outbox transaction test"),
      isCompleted = false,
      createdAt = LocalDateTime.now(),
      updatedAt = None,
      deletedAt = None,
      isDeleted = false,
      tenantId = tenantId,
      dueDate = Some(LocalDateTime.now().plusDays(2))
    )

  private def cleanupFixture(fixture: Fixture, todoId: Option[UUID]): Unit = {
    database.withConnection { conn =>
      todoId.foreach { id =>
        val deleteOutbox = conn.prepareStatement("DELETE FROM todo_event_outbox WHERE aggregate_id = ?")
        deleteOutbox.setString(1, id.toString)
        deleteOutbox.executeUpdate()

        val deleteTodo = conn.prepareStatement("DELETE FROM todos WHERE id = ?")
        deleteTodo.setString(1, id.toString)
        deleteTodo.executeUpdate()
      }

      val deleteUser = conn.prepareStatement("DELETE FROM users WHERE id = ?")
      deleteUser.setString(1, fixture.user.id.toString)
      deleteUser.executeUpdate()

      val deleteTenant = conn.prepareStatement("DELETE FROM tenants WHERE id = ?")
      deleteTenant.setString(1, fixture.tenant.id.toString)
      deleteTenant.executeUpdate()
    }
  }
}
