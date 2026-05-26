package kafka.publisher

import org.scalatestplus.play.PlaySpec
import play.api.Configuration

class KafkaProducerSettingsLoaderSpec extends PlaySpec {

  "KafkaProducerSettingsLoader" should {

    "load explicit producer settings from configuration" in {
      val configuration = Configuration(
        "kafka.enabled" -> true,
        "kafka.bootstrapServers" -> "kafka1:9092,kafka2:9092",
        "kafka.clientId" -> "todo-phase-3",
        "kafka.topic.todoEvents" -> "todo.events.v1",
        "kafka.producer.acks" -> "all",
        "kafka.producer.enableIdempotence" -> true,
        "kafka.producer.requestTimeoutMs" -> 45000,
        "kafka.producer.deliveryTimeoutMs" -> 150000,
        "kafka.producer.maxInFlightRequestsPerConnection" -> 3
      )

      val settings = new KafkaProducerSettingsLoader(configuration).load()

      settings.enabled mustBe true
      settings.bootstrapServers mustBe "kafka1:9092,kafka2:9092"
      settings.clientId mustBe "todo-phase-3"
      settings.todoEventsTopic mustBe "todo.events.v1"
      settings.acks mustBe "all"
      settings.enableIdempotence mustBe true
      settings.requestTimeoutMs mustBe 45000
      settings.deliveryTimeoutMs mustBe 150000
      settings.maxInFlightRequestsPerConnection mustBe 3
    }

    "fall back to safe defaults when configuration is absent" in {
      val settings = new KafkaProducerSettingsLoader(Configuration.empty).load()

      settings.enabled mustBe false
      settings.bootstrapServers mustBe "localhost:9092"
      settings.clientId mustBe "todo-play-app"
      settings.todoEventsTopic mustBe "todo.events.v1"
      settings.acks mustBe "all"
      settings.enableIdempotence mustBe true
      settings.requestTimeoutMs mustBe 30000
      settings.deliveryTimeoutMs mustBe 120000
      settings.maxInFlightRequestsPerConnection mustBe 5
    }
  }
}
