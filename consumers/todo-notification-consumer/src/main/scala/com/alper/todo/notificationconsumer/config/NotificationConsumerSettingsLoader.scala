package com.alper.todo.notificationconsumer.config

import com.alper.todo.notificationconsumer.model.NotificationDispatchMode
import com.typesafe.config.{Config, ConfigFactory}

object NotificationConsumerSettingsLoader {

  def load(): NotificationConsumerSettings =
    load(ConfigFactory.load())

  def load(rootConfig: Config): NotificationConsumerSettings = {
    val config = rootConfig.getConfig("notification-consumer")
    val db = config.getConfig("database")

    NotificationConsumerSettings(
      bootstrapServers = config.getString("bootstrapServers"),
      topic = config.getString("topic"),
      groupId = config.getString("groupId"),
      consumerName = config.getString("consumerName"),
      dlqTopic = config.getString("dlqTopic"),
      dispatchMode = NotificationDispatchMode.fromString(config.getString("dispatchMode")),
      supportedEventVersion = config.getInt("supportedEventVersion"),
      maxRetries = config.getInt("maxRetries"),
      retryBackoffMillis = config.getLong("retryBackoffMillis"),
      database = NotificationConsumerDatabaseSettings(
        driver = db.getString("driver"),
        url = db.getString("url"),
        username = db.getString("username"),
        password = db.getString("password")
      )
    )
  }
}
