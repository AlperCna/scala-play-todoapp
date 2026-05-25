package events

import models.Todo
import play.api.libs.json.{JsObject, Json}

import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}

@Singleton
class TodoEventFactory @Inject()() {

  private val EventVersion = 1
  private val EntityType   = "todo"

  def todoCreated(todo: Todo, correlationId: Option[String] = None): DomainEventEnvelope =
    envelope("TodoCreated", todo, correlationId)

  def todoCompleted(todo: Todo, correlationId: Option[String] = None): DomainEventEnvelope =
    envelope("TodoCompleted", todo, correlationId)

  def todoUpdated(todo: Todo, correlationId: Option[String] = None): DomainEventEnvelope =
    envelope("TodoUpdated", todo, correlationId)

  def todoDeleted(todo: Todo, correlationId: Option[String] = None): DomainEventEnvelope =
    envelope("TodoDeleted", todo, correlationId)

  private def envelope(
    eventType: String,
    todo: Todo,
    correlationId: Option[String]
  ): DomainEventEnvelope =
    DomainEventEnvelope(
      eventId = UUID.randomUUID(),
      eventType = eventType,
      eventVersion = EventVersion,
      occurredAt = Instant.now(),
      tenantId = todo.tenantId,
      userId = todo.userId,
      entityType = EntityType,
      entityId = todo.id,
      correlationId = correlationId,
      payload = todoPayload(todo)
    )

  private def todoPayload(todo: Todo): JsObject =
    Json.obj(
      "todoId" -> todo.id.toString,
      "title" -> todo.title,
      "description" -> todo.description,
      "isCompleted" -> todo.isCompleted,
      "dueDate" -> todo.dueDate.map(_.toString),
      "createdAt" -> todo.createdAt.toString,
      "updatedAt" -> todo.updatedAt.map(_.toString),
      "deletedAt" -> todo.deletedAt.map(_.toString),
      "tenantId" -> todo.tenantId.toString,
      "userId" -> todo.userId.toString
    )
}
