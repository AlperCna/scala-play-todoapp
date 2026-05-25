package services

import events.DomainEventEnvelope

import scala.concurrent.Future

trait TodoEventPublisher {
  def publish(event: DomainEventEnvelope): Future[Unit]
}
