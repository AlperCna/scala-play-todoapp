package kafka.publisher

import kafka.events.DomainEventEnvelope

import scala.concurrent.Future

trait TodoEventPublisher {
  def publish(event: DomainEventEnvelope): Future[Unit]
}
