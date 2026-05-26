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
  private case object PublishCompleted
}

class TodoOutboxPublisherActor(
  publishService: TodoOutboxPublishService,
  settingsLoader: TodoOutboxWorkerSettingsLoader
)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  import TodoOutboxPublisherActor._
  private var publishInProgress = false

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
      if (publishInProgress) {
        log.warning("[TodoOutboxPublisher] Onceki batch hala calisiyor. Bu tick atlandi.")
      } else {
        publishInProgress = true
        publishService.publishPendingBatch().onComplete { result =>
          result.foreach { summary =>
            if (!summary.skipped && summary.processed > 0) {
              log.info(
                s"[TodoOutboxPublisher] processed=${summary.processed}, published=${summary.published}, retried=${summary.retried}, failed=${summary.failed}"
              )
            }
          }

          result.failed.foreach { ex =>
            log.error(ex, "[TodoOutboxPublisher] Batch publish sirasinda beklenmeyen hata.")
          }

          self ! PublishCompleted
        }
      }

    case PublishCompleted =>
      publishInProgress = false
  }
}
