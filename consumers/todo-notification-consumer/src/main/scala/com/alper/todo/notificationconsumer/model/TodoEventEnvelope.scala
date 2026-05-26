package com.alper.todo.notificationconsumer.model

import java.time.{Instant, LocalDateTime}
import java.util.UUID

case class TodoPayload(
  todoId: UUID,
  title: String,
  description: Option[String],
  isCompleted: Boolean,
  dueDate: Option[LocalDateTime],
  createdAt: LocalDateTime,
  updatedAt: Option[LocalDateTime],
  deletedAt: Option[LocalDateTime],
  tenantId: UUID,
  userId: UUID
)

case class TodoEventEnvelope(
  eventId: UUID,
  eventType: String,
  eventVersion: Int,
  occurredAt: Instant,
  tenantId: UUID,
  userId: UUID,
  entityType: String,
  entityId: UUID,
  correlationId: Option[String],
  payload: TodoPayload
)
