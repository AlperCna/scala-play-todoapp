package com.alper.todo.notificationconsumer.config

import com.alper.todo.notificationconsumer.model.NotificationDispatchMode

case class NotificationConsumerDatabaseSettings(
  driver: String,
  url: String,
  username: String,
  password: String
)

case class NotificationConsumerSettings(
  bootstrapServers: String,
  topic: String,
  groupId: String,
  consumerName: String,
  dlqTopic: String,
  dispatchMode: NotificationDispatchMode,
  supportedEventVersion: Int,
  maxRetries: Int,
  retryBackoffMillis: Long,
  database: NotificationConsumerDatabaseSettings
)
