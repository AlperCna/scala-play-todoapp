package com.alper.todo.notificationconsumer.config

import com.alper.todo.notificationconsumer.model.NotificationDispatchMode

case class NotificationConsumerSettings(
  bootstrapServers: String,
  topic: String,
  groupId: String,
  dispatchMode: NotificationDispatchMode,
  supportedEventVersion: Int
)
