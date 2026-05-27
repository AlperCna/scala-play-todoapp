package com.alper.todo.auditconsumer.config

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AuditConsumerSettingsLoaderSpec extends AnyWordSpec with Matchers {

  "AuditConsumerSettingsLoader" should {
    "load audit consumer settings from config" in {
      val config = ConfigFactory.parseString(
        """
          |audit-consumer {
          |  bootstrapServers = "localhost:9092"
          |  topic = "todo.events.v1"
          |  groupId = "todo-audit-consumer-v1"
          |  consumerName = "todo-audit-consumer-v1"
          |  supportedEventVersion = 2
          |  database {
          |    driver = "driver"
          |    url = "jdbc:test"
          |    username = "sa"
          |    password = "pw"
          |  }
          |}
          |""".stripMargin
      )

      val settings = AuditConsumerSettingsLoader.load(config)

      settings.bootstrapServers shouldBe "localhost:9092"
      settings.topic shouldBe "todo.events.v1"
      settings.groupId shouldBe "todo-audit-consumer-v1"
      settings.consumerName shouldBe "todo-audit-consumer-v1"
      settings.supportedEventVersion shouldBe 2
      settings.database.url shouldBe "jdbc:test"
    }
  }
}
