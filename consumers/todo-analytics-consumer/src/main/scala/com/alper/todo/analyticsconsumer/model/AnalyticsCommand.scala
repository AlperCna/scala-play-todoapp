package com.alper.todo.analyticsconsumer.model

import java.time.LocalDateTime
import java.util.UUID

case class AnalyticsCommand(
  eventId: UUID,
  eventType: String,
  tenantId: UUID,
  userId: UUID,
  todoId: UUID,
  title: String,
  description: Option[String],
  isCompleted: Boolean,
  dueDate: Option[LocalDateTime],
  createdAt: LocalDateTime,
  updatedAt: Option[LocalDateTime],
  deletedAt: Option[LocalDateTime],
  occurredAt: LocalDateTime
)
