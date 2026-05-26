package com.alper.todo.notificationconsumer.service

import com.alper.todo.notificationconsumer.config.NotificationConsumerSettings
import com.alper.todo.notificationconsumer.model.NotificationDispatchMode.Disabled
import com.alper.todo.notificationconsumer.model.NotificationProcessingResult._
import com.alper.todo.notificationconsumer.model.{NotificationProcessingResult, TodoEventEnvelope}
import com.alper.todo.notificationconsumer.ports.{NotificationSender, ProcessedEventStore}

import scala.concurrent.{ExecutionContext, Future}

class NotificationEventProcessor(
  settings: NotificationConsumerSettings,
  processedEventStore: ProcessedEventStore,
  notificationSender: NotificationSender,
  commandFactory: NotificationCommandFactory
)(implicit ec: ExecutionContext) {

  def process(envelope: TodoEventEnvelope): Future[NotificationProcessingResult] =
    if (settings.dispatchMode == Disabled) {
      Future.successful(DisabledIgnored)
    } else if (envelope.eventVersion != settings.supportedEventVersion) {
      Future.successful(UnsupportedVersionIgnored)
    } else if (!commandFactory.supports(envelope.eventType)) {
      Future.successful(UnsupportedEventIgnored)
    } else {
      processedEventStore.contains(envelope.eventId).flatMap { alreadyProcessed =>
        if (alreadyProcessed) {
          Future.successful(DuplicateIgnored)
        } else {
          val command = commandFactory.fromEvent(envelope, settings.dispatchMode)

          for {
            _ <- notificationSender.send(command)
            _ <- processedEventStore.markProcessed(envelope.eventId)
          } yield Processed
        }
      }
    }
}
