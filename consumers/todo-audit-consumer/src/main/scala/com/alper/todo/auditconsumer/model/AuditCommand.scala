package com.alper.todo.auditconsumer.model

import java.time.LocalDateTime
import java.util.UUID

case class AuditCommand(
  eventId: UUID,
  tenantId: UUID,
  userId: UUID,
  action: String,
  createdAt: LocalDateTime
)
