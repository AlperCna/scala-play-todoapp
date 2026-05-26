package kafka.publisher

import kafka.events.DomainEventEnvelope

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class KafkaTodoEventPublisher @Inject()(
  settingsLoader: KafkaProducerSettingsLoader,
  recordFactory: KafkaTodoEventRecordFactory,
  producerClient: KafkaProducerClient
) extends TodoEventPublisher {

  override def publish(event: DomainEventEnvelope): Future[Unit] = {
    val settings = settingsLoader.load()
    val record = recordFactory.buildRecord(event, settings)
    producerClient.send(record)
  }
}
