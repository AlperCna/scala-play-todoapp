package actors

import akka.actor.{Actor, ActorLogging, Props}
import kafka.outbox.{TodoOutboxPublishService, TodoOutboxWorkerSettingsLoader}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object TodoOutboxPublisherActor {
  def props(
    publishService: TodoOutboxPublishService,
    settingsLoader: TodoOutboxWorkerSettingsLoader
  )(implicit ec: ExecutionContext): Props =
    Props(new TodoOutboxPublisherActor(publishService, settingsLoader))

  case object PublishPending
}

class TodoOutboxPublisherActor(
  publishService: TodoOutboxPublishService,
  settingsLoader: TodoOutboxWorkerSettingsLoader
)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  import TodoOutboxPublisherActor._

  override def preStart(): Unit = {
    val settings = settingsLoader.load()
    val interval = settings.pollIntervalSeconds.seconds

    log.info(
      s"[TodoOutboxPublisher] Basliyor. Kafka enabled=${settings.enabled}, interval=${settings.pollIntervalSeconds}s, batchSize=${settings.batchSize}"
    )

    context.system.scheduler.scheduleWithFixedDelay(
      initialDelay = interval,
      delay = interval,
      receiver = self,
      message = PublishPending
    )
  }

  override def receive: Receive = {
    case PublishPending =>
      publishService.publishPendingBatch().foreach { publishedCount =>
        if (publishedCount > 0) {
          log.info(s"[TodoOutboxPublisher] $publishedCount adet outbox kaydi publish edildi.")
        }
      }
  }
}
