package kafka.outbox

import kafka.publisher.{KafkaProducerSettingsLoader, KafkaTodoEventPublisher}

import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TodoOutboxPublishService @Inject()(
  outboxRepository: TodoOutboxRepository,
  envelopeFactory: TodoOutboxEnvelopeFactory,
  kafkaPublisher: KafkaTodoEventPublisher,
  producerSettingsLoader: KafkaProducerSettingsLoader,
  workerSettingsLoader: TodoOutboxWorkerSettingsLoader
)(implicit ec: ExecutionContext) {

  def publishPendingBatch(): Future[TodoOutboxPublishResult] = {
    val producerSettings = producerSettingsLoader.load()
    val workerSettings = workerSettingsLoader.load()

    if (!producerSettings.enabled || !workerSettings.enabled) {
      Future.successful(TodoOutboxPublishResult.Skipped)
    } else {
      val now = LocalDateTime.now(ZoneOffset.UTC)

      outboxRepository.findPublishable(workerSettings.batchSize, now).flatMap { events =>
        Future
          .sequence(events.map(processEvent(_, now, workerSettings)))
          .map(toResult(_, events.size))
      }
    }
  }

  private def processEvent(
    outboxEvent: TodoOutboxEvent,
    now: LocalDateTime,
    settings: TodoOutboxWorkerSettings
  ): Future[String] = {
    kafkaPublisher
      .publish(envelopeFactory.toEnvelope(outboxEvent))
      .flatMap(_ => outboxRepository.markPublished(outboxEvent.id, now).map(_ => "published"))
      .recoverWith { case ex =>
        val nextAttemptCount = outboxEvent.attemptCount + 1
        val failed = nextAttemptCount >= settings.maxAttempts
        val retryAt = now.plusSeconds(backoffSeconds(nextAttemptCount, settings))
        val nextStatus = if (failed) TodoOutboxStatus.Failed else TodoOutboxStatus.Pending

        outboxRepository
          .markFailure(
            id = outboxEvent.id,
            nextAttemptCount = nextAttemptCount,
            nextAvailableAt = retryAt,
            lastError = Option(ex.getMessage).getOrElse(ex.getClass.getSimpleName),
            nextStatus = nextStatus
          )
          .map(_ => if (failed) "failed" else "retried")
      }
  }

  private def backoffSeconds(attemptCount: Int, settings: TodoOutboxWorkerSettings): Int = {
    val multiplier = Math.pow(2.0, Math.max(0, attemptCount - 1).toDouble).toInt
    settings.initialRetryDelaySeconds * multiplier
  }

  private def toResult(outcomes: Seq[String], processed: Int): TodoOutboxPublishResult =
    TodoOutboxPublishResult(
      processed = processed,
      published = outcomes.count(_ == "published"),
      retried = outcomes.count(_ == "retried"),
      failed = outcomes.count(_ == "failed"),
      skipped = false
    )
}
