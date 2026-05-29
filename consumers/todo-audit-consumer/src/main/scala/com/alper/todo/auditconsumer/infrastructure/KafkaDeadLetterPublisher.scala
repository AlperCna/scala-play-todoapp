package com.alper.todo.auditconsumer.infrastructure

import com.alper.todo.auditconsumer.model.DeadLetterEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import play.api.libs.json.Json

import java.time.Instant
import java.util.Properties

class KafkaDeadLetterPublisher(
  bootstrapServers: String,
  dlqTopic: String,
  consumerName: String
) {

  private val producer = new KafkaProducer[String, String](producerProperties(bootstrapServers))

  def publish(record: ConsumerRecord[String, String], reason: String, errorMessage: Option[String]): Unit = {
    val payload = Json.stringify(
      Json.toJson(
        DeadLetterEvent(
          consumerName = consumerName,
          originalTopic = record.topic(),
          originalKey = Option(record.key()),
          originalPartition = record.partition(),
          originalOffset = record.offset(),
          reason = reason,
          errorMessage = errorMessage,
          rawPayload = record.value(),
          receivedAt = Instant.now()
        )
      )
    )

    producer.send(new ProducerRecord[String, String](dlqTopic, record.key(), payload)).get()
  }

  def close(): Unit =
    producer.close()

  private def producerProperties(bootstrapServers: String): Properties = {
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    props.put(ProducerConfig.ACKS_CONFIG, "all")
    props
  }
}
