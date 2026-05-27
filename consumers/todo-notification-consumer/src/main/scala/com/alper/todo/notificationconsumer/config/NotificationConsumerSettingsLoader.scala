package com.alper.todo.notificationconsumer.config

import com.alper.todo.notificationconsumer.model.NotificationDispatchMode
import com.typesafe.config.{Config, ConfigFactory}

object NotificationConsumerSettingsLoader {

  def load(): NotificationConsumerSettings =
    load(ConfigFactory.load())

  def load(rootConfig: Config): NotificationConsumerSettings = {
    val config = rootConfig.getConfig("notification-consumer")

    NotificationConsumerSettings(
      bootstrapServers = config.getString("bootstrapServers"),
      topic = config.getString("topic"),
      groupId = config.getString("groupId"),
      dispatchMode = NotificationDispatchMode.fromString(config.getString("dispatchMode")),
      supportedEventVersion = config.getInt("supportedEventVersion")
    )
  }
}
