package com.alper.todo.notificationconsumer.model

import java.util.UUID

case class NotificationCommand(
  eventId: UUID,
  tenantId: UUID,
  userId: UUID,
  dispatchMode: NotificationDispatchMode,
  subject: String,
  body: String
)
