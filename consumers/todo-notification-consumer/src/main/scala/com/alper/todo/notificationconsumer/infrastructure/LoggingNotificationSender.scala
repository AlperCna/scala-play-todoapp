package com.alper.todo.notificationconsumer.infrastructure

import com.alper.todo.notificationconsumer.model.NotificationCommand
import com.alper.todo.notificationconsumer.ports.NotificationSender

import scala.concurrent.{ExecutionContext, Future}

class LoggingNotificationSender(implicit ec: ExecutionContext) extends NotificationSender {

  override def send(command: NotificationCommand): Future[Unit] = Future.successful {
    val prefix = command.dispatchMode.value.toUpperCase
    println(
      s"[$prefix] notification eventId=${command.eventId} tenantId=${command.tenantId} userId=${command.userId} subject='${command.subject}'"
    )
    ()
  }
}
