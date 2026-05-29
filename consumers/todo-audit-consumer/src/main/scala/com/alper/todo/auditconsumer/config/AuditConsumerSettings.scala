package com.alper.todo.auditconsumer.config

case class AuditConsumerDatabaseSettings(
  driver: String,
  url: String,
  username: String,
  password: String
)

case class AuditConsumerSettings(
  bootstrapServers: String,
  topic: String,
  groupId: String,
  consumerName: String,
  dlqTopic: String,
  supportedEventVersion: Int,
  maxRetries: Int,
  retryBackoffMillis: Long,
  database: AuditConsumerDatabaseSettings
)
