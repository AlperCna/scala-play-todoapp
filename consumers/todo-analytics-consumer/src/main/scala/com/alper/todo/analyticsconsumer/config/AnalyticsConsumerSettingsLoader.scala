package com.alper.todo.analyticsconsumer.config

import com.typesafe.config.{Config, ConfigFactory}

object AnalyticsConsumerSettingsLoader {

  def load(): AnalyticsConsumerSettings =
    load(ConfigFactory.load())

  def load(rootConfig: Config): AnalyticsConsumerSettings = {
    val config = rootConfig.getConfig("analytics-consumer")
    val db = config.getConfig("database")

    AnalyticsConsumerSettings(
      bootstrapServers = config.getString("bootstrapServers"),
      topic = config.getString("topic"),
      groupId = config.getString("groupId"),
      consumerName = config.getString("consumerName"),
      supportedEventVersion = config.getInt("supportedEventVersion"),
      database = AnalyticsConsumerDatabaseSettings(
        driver = db.getString("driver"),
        url = db.getString("url"),
        username = db.getString("username"),
        password = db.getString("password")
      )
    )
  }
}
