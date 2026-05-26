package kafka.publisher

import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class KafkaProducerSettingsLoader @Inject()(configuration: Configuration) {

  def load(): KafkaProducerSettings =
    KafkaProducerSettings(
      enabled = configuration.getOptional[Boolean]("kafka.enabled").getOrElse(false),
      bootstrapServers = configuration.getOptional[String]("kafka.bootstrapServers").getOrElse("localhost:9092"),
      clientId = configuration.getOptional[String]("kafka.clientId").getOrElse("todo-play-app"),
      todoEventsTopic = configuration.getOptional[String]("kafka.topic.todoEvents").getOrElse("todo.events.v1"),
      acks = configuration.getOptional[String]("kafka.producer.acks").getOrElse("all"),
      enableIdempotence = configuration.getOptional[Boolean]("kafka.producer.enableIdempotence").getOrElse(true),
      requestTimeoutMs = configuration.getOptional[Int]("kafka.producer.requestTimeoutMs").getOrElse(30000),
      deliveryTimeoutMs = configuration.getOptional[Int]("kafka.producer.deliveryTimeoutMs").getOrElse(120000),
      maxInFlightRequestsPerConnection =
        configuration.getOptional[Int]("kafka.producer.maxInFlightRequestsPerConnection").getOrElse(5)
    )
}
