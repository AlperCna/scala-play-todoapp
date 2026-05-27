package com.alper.todo.auditconsumer.config

import com.typesafe.config.{Config, ConfigFactory}

object AuditConsumerSettingsLoader {

  def load(): AuditConsumerSettings =
    load(ConfigFactory.load())

  def load(rootConfig: Config): AuditConsumerSettings = {
    val config = rootConfig.getConfig("audit-consumer")
    val db = config.getConfig("database")

    AuditConsumerSettings(
      bootstrapServers = config.getString("bootstrapServers"),
      topic = config.getString("topic"),
      groupId = config.getString("groupId"),
      consumerName = config.getString("consumerName"),
      supportedEventVersion = config.getInt("supportedEventVersion"),
      database = AuditConsumerDatabaseSettings(
        driver = db.getString("driver"),
        url = db.getString("url"),
        username = db.getString("username"),
        password = db.getString("password")
      )
    )
  }
}
