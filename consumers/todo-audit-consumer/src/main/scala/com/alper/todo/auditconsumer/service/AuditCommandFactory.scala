package com.alper.todo.auditconsumer.service

import com.alper.todo.auditconsumer.model.{AuditCommand, TodoEventEnvelope}

import java.time.{LocalDateTime, ZoneOffset}

class AuditCommandFactory {

  private val SupportedTypes = Set("TodoCreated", "TodoUpdated", "TodoCompleted", "TodoDeleted")

  def supports(eventType: String): Boolean =
    SupportedTypes.contains(eventType)

  def fromEvent(envelope: TodoEventEnvelope): AuditCommand = {
    val action =
      envelope.eventType match {
        case "TodoCreated"   => s"TODO_EVENT_CREATED: ${envelope.payload.title}"
        case "TodoUpdated"   => s"TODO_EVENT_UPDATED: ${envelope.payload.title}"
        case "TodoCompleted" => s"TODO_EVENT_COMPLETED: ${envelope.payload.title}"
        case "TodoDeleted"   => s"TODO_EVENT_DELETED: ${envelope.payload.title}"
        case other           => s"TODO_EVENT_UNHANDLED: $other"
      }

    AuditCommand(
      eventId = envelope.eventId,
      tenantId = envelope.tenantId,
      userId = envelope.userId,
      action = action,
      createdAt = LocalDateTime.ofInstant(envelope.occurredAt, ZoneOffset.UTC)
    )
  }
}
