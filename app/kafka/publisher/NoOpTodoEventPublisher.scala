package kafka.publisher

import kafka.events.DomainEventEnvelope

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class NoOpTodoEventPublisher @Inject()() extends TodoEventPublisher {
  override def publish(event: DomainEventEnvelope): Future[Unit] =
    Future.successful(())
}
