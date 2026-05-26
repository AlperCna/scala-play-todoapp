package kafka.publisher

import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.Future

trait KafkaProducerClient {
  def send(record: ProducerRecord[String, String]): Future[Unit]
}
