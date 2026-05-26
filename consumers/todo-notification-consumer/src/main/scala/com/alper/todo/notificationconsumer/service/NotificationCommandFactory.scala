package com.alper.todo.notificationconsumer.service

import com.alper.todo.notificationconsumer.model.{NotificationCommand, NotificationDispatchMode, TodoEventEnvelope}

class NotificationCommandFactory {

  private val SupportedTypes = Set("TodoCreated", "TodoCompleted")

  def supports(eventType: String): Boolean =
    SupportedTypes.contains(eventType)

  def fromEvent(
    envelope: TodoEventEnvelope,
    dispatchMode: NotificationDispatchMode
  ): NotificationCommand = {
    val subject =
      envelope.eventType match {
        case "TodoCreated" =>
          s"New todo created: ${envelope.payload.title}"
        case "TodoCompleted" =>
          s"Todo completed: ${envelope.payload.title}"
        case other =>
          s"Unhandled todo event: $other"
      }

    val body =
      envelope.eventType match {
        case "TodoCreated" =>
          s"Todo '${envelope.payload.title}' was created for user ${envelope.userId} in tenant ${envelope.tenantId}."
        case "TodoCompleted" =>
          s"Todo '${envelope.payload.title}' was completed by user ${envelope.userId} in tenant ${envelope.tenantId}."
        case other =>
          s"Event '$other' arrived for todo ${envelope.payload.todoId}."
      }

    NotificationCommand(
      eventId = envelope.eventId,
      tenantId = envelope.tenantId,
      userId = envelope.userId,
      dispatchMode = dispatchMode,
      subject = subject,
      body = body
    )
  }
}
