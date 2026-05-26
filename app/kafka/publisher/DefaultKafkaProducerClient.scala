package kafka.publisher

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer

import java.util.Properties
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future, Promise}

@Singleton
class DefaultKafkaProducerClient @Inject()(
  settingsLoader: KafkaProducerSettingsLoader
)(implicit ec: ExecutionContext) extends KafkaProducerClient {

  private lazy val producer = {
    val settings = settingsLoader.load()
    val props = new Properties()

    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.bootstrapServers)
    props.put(ProducerConfig.CLIENT_ID_CONFIG, settings.clientId)
    props.put(ProducerConfig.ACKS_CONFIG, settings.acks)
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, settings.enableIdempotence.toString)
    props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, settings.requestTimeoutMs.toString)
    props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, settings.deliveryTimeoutMs.toString)
    props.put(
      ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
      settings.maxInFlightRequestsPerConnection.toString
    )
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)

    new KafkaProducer[String, String](props)
  }

  override def send(record: ProducerRecord[String, String]): Future[Unit] = {
    val promise = Promise[Unit]()

    producer.send(
      record,
      (metadata, exception) =>
        if (exception != null) promise.failure(exception)
        else promise.success(())
    )

    promise.future
  }
}
