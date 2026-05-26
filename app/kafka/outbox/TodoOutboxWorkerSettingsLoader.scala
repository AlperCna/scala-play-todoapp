package kafka.outbox

import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class TodoOutboxWorkerSettingsLoader @Inject()(configuration: Configuration) {

  def load(): TodoOutboxWorkerSettings =
    TodoOutboxWorkerSettings(
      enabled = configuration.getOptional[Boolean]("kafka.enabled").getOrElse(false),
      pollIntervalSeconds = configuration.getOptional[Int]("kafka.outbox.pollIntervalSeconds").getOrElse(15),
      batchSize = configuration.getOptional[Int]("kafka.outbox.batchSize").getOrElse(20),
      maxAttempts = configuration.getOptional[Int]("kafka.outbox.maxAttempts").getOrElse(5),
      initialRetryDelaySeconds = configuration.getOptional[Int]("kafka.outbox.initialRetryDelaySeconds").getOrElse(30)
    )
}
