package kafka.publisher

import kafka.events.DomainEventEnvelope

import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.Future

@Singleton
class ConfigurableTodoEventPublisher @Inject()(
  settingsLoader: KafkaProducerSettingsLoader,
  kafkaPublisherProvider: Provider[KafkaTodoEventPublisher],
  noOpTodoEventPublisher: NoOpTodoEventPublisher
) extends TodoEventPublisher {

  override def publish(event: DomainEventEnvelope): Future[Unit] = {
    val settings = settingsLoader.load()

    if (settings.enabled) kafkaPublisherProvider.get().publish(event)
    else noOpTodoEventPublisher.publish(event)
  }
}
