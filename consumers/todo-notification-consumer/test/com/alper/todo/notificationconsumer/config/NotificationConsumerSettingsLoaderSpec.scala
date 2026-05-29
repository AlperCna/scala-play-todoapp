package com.alper.todo.notificationconsumer.config

import com.alper.todo.notificationconsumer.model.NotificationDispatchMode.{Live, Sandbox}
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NotificationConsumerSettingsLoaderSpec extends AnyWordSpec with Matchers {

  "NotificationConsumerSettingsLoader" should {
    "load all consumer settings from config" in {
      val config = ConfigFactory.parseString(
        """
          |notification-consumer {
          |  bootstrapServers = "localhost:9092"
          |  topic = "todo.events.v1"
          |  groupId = "todo-notification-consumer-v1"
          |  consumerName = "todo-notification-consumer-v1"
          |  dlqTopic = "todo.events.dlq.v1"
          |  dispatchMode = "live"
          |  supportedEventVersion = 2
          |  maxRetries = 4
          |  retryBackoffMillis = 500
          |  database {
          |    driver = "driver"
          |    url = "jdbc:test"
          |    username = "user"
          |    password = "pass"
          |  }
          |}
          |""".stripMargin
      )

      val settings = NotificationConsumerSettingsLoader.load(config)

      settings.bootstrapServers shouldBe "localhost:9092"
      settings.topic shouldBe "todo.events.v1"
      settings.groupId shouldBe "todo-notification-consumer-v1"
      settings.consumerName shouldBe "todo-notification-consumer-v1"
      settings.dlqTopic shouldBe "todo.events.dlq.v1"
      settings.dispatchMode shouldBe Live
      settings.supportedEventVersion shouldBe 2
      settings.maxRetries shouldBe 4
      settings.retryBackoffMillis shouldBe 500L
      settings.database.url shouldBe "jdbc:test"
    }

    "default unknown dispatch modes to sandbox" in {
      val config = ConfigFactory.parseString(
        """
          |notification-consumer {
          |  bootstrapServers = "localhost:9092"
          |  topic = "todo.events.v1"
          |  groupId = "todo-notification-consumer-v1"
          |  consumerName = "todo-notification-consumer-v1"
          |  dlqTopic = "todo.events.dlq.v1"
          |  dispatchMode = "mystery"
          |  supportedEventVersion = 1
          |  maxRetries = 3
          |  retryBackoffMillis = 250
          |  database {
          |    driver = "driver"
          |    url = "jdbc:test"
          |    username = "user"
          |    password = "pass"
          |  }
          |}
          |""".stripMargin
      )

      NotificationConsumerSettingsLoader.load(config).dispatchMode shouldBe Sandbox
    }
  }
}
