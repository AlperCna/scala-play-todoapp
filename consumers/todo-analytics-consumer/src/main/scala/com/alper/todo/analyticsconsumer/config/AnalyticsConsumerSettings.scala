package com.alper.todo.analyticsconsumer.config

case class AnalyticsConsumerDatabaseSettings(
  driver: String,
  url: String,
  username: String,
  password: String
)

case class AnalyticsConsumerSettings(
  bootstrapServers: String,
  topic: String,
  groupId: String,
  consumerName: String,
  dlqTopic: String,
  supportedEventVersion: Int,
  maxRetries: Int,
  retryBackoffMillis: Long,
  database: AnalyticsConsumerDatabaseSettings
)
