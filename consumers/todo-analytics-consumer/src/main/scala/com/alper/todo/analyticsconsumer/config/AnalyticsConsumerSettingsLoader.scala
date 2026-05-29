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
      dlqTopic = config.getString("dlqTopic"),
      supportedEventVersion = config.getInt("supportedEventVersion"),
      maxRetries = config.getInt("maxRetries"),
      retryBackoffMillis = config.getLong("retryBackoffMillis"),
      database = AnalyticsConsumerDatabaseSettings(
        driver = db.getString("driver"),
        url = db.getString("url"),
        username = db.getString("username"),
        password = db.getString("password")
      )
    )
  }
}
