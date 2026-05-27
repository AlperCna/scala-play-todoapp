package com.alper.todo.analyticsconsumer.service

import com.alper.todo.analyticsconsumer.json.TodoEventJson
import com.alper.todo.analyticsconsumer.model.{AnalyticsCommand, TodoEventEnvelope}

import java.time.{LocalDateTime, ZoneOffset}

class AnalyticsCommandFactory {

  private val supportedEventTypes =
    Set("TodoCreated", "TodoUpdated", "TodoCompleted", "TodoDeleted")

  def supports(eventType: String): Boolean =
    supportedEventTypes.contains(eventType)

  def fromEvent(envelope: TodoEventEnvelope): AnalyticsCommand = {
    val payload = envelope.payload

    AnalyticsCommand(
      eventId = envelope.eventId,
      eventType = envelope.eventType,
      tenantId = envelope.tenantId,
      userId = envelope.userId,
      todoId = TodoEventJson.uuidField(payload, "todoId"),
      title = TodoEventJson.requiredString(payload, "title"),
      description = TodoEventJson.optionalString(payload, "description"),
      isCompleted = TodoEventJson.requiredBoolean(payload, "isCompleted"),
      dueDate = TodoEventJson.optionalString(payload, "dueDate").map(LocalDateTime.parse),
      createdAt = TodoEventJson.optionalString(payload, "createdAt")
        .map(LocalDateTime.parse)
        .getOrElse(LocalDateTime.ofInstant(envelope.occurredAt, ZoneOffset.UTC)),
      updatedAt = TodoEventJson.optionalString(payload, "updatedAt").map(LocalDateTime.parse),
      deletedAt = TodoEventJson.optionalString(payload, "deletedAt").map(LocalDateTime.parse),
      occurredAt = LocalDateTime.ofInstant(envelope.occurredAt, ZoneOffset.UTC)
    )
  }
}
