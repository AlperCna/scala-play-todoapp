package com.alper.todo.notificationconsumer.ports

import com.alper.todo.notificationconsumer.model.NotificationCommand

import scala.concurrent.Future

trait NotificationSender {
  def send(command: NotificationCommand): Future[Unit]
}
