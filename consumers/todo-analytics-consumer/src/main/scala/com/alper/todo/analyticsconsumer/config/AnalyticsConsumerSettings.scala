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
  supportedEventVersion: Int,
  database: AnalyticsConsumerDatabaseSettings
)
