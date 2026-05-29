package com.alper.todo.analyticsconsumer.config

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AnalyticsConsumerSettingsLoaderSpec extends AnyWordSpec with Matchers {

  "AnalyticsConsumerSettingsLoader" should {
    "load analytics consumer settings from config" in {
      val config = ConfigFactory.parseString(
        """
          |analytics-consumer {
          |  bootstrapServers = "localhost:9092"
          |  topic = "todo.events.v1"
          |  groupId = "analytics-group"
          |  consumerName = "todo-analytics-consumer"
          |  dlqTopic = "todo.events.dlq.v1"
          |  supportedEventVersion = 1
          |  maxRetries = 3
          |  retryBackoffMillis = 250
          |
          |  database {
          |    driver = "driver"
          |    url = "url"
          |    username = "user"
          |    password = "pass"
          |  }
          |}
          |""".stripMargin
      )

      val settings = AnalyticsConsumerSettingsLoader.load(config)

      settings.bootstrapServers mustBe "localhost:9092"
      settings.topic mustBe "todo.events.v1"
      settings.groupId mustBe "analytics-group"
      settings.consumerName mustBe "todo-analytics-consumer"
      settings.dlqTopic mustBe "todo.events.dlq.v1"
      settings.supportedEventVersion mustBe 1
      settings.maxRetries mustBe 3
      settings.retryBackoffMillis mustBe 250L
      settings.database.driver mustBe "driver"
      settings.database.url mustBe "url"
      settings.database.username mustBe "user"
      settings.database.password mustBe "pass"
    }
  }
}
