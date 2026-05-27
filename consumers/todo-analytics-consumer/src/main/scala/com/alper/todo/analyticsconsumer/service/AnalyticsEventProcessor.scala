package com.alper.todo.analyticsconsumer.service

import com.alper.todo.analyticsconsumer.config.AnalyticsConsumerSettings
import com.alper.todo.analyticsconsumer.model.AnalyticsProcessingResult._
import com.alper.todo.analyticsconsumer.model.{AnalyticsProcessingResult, TodoEventEnvelope}
import com.alper.todo.analyticsconsumer.ports.AnalyticsProjectionWriter

import scala.concurrent.{ExecutionContext, Future}

class AnalyticsEventProcessor(
  settings: AnalyticsConsumerSettings,
  projectionWriter: AnalyticsProjectionWriter,
  commandFactory: AnalyticsCommandFactory
)(implicit ec: ExecutionContext) {

  def process(envelope: TodoEventEnvelope): Future[AnalyticsProcessingResult] =
    if (envelope.eventVersion != settings.supportedEventVersion) {
      Future.successful(UnsupportedVersionIgnored)
    } else if (!commandFactory.supports(envelope.eventType)) {
      Future.successful(UnsupportedEventIgnored)
    } else {
      projectionWriter.writeIfNew(commandFactory.fromEvent(envelope))
    }
}
