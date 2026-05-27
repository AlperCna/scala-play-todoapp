package com.alper.todo.auditconsumer.service

import com.alper.todo.auditconsumer.config.AuditConsumerSettings
import com.alper.todo.auditconsumer.model.AuditProcessingResult._
import com.alper.todo.auditconsumer.model.{AuditProcessingResult, TodoEventEnvelope}
import com.alper.todo.auditconsumer.ports.AuditLogWriter

import scala.concurrent.{ExecutionContext, Future}

class AuditEventProcessor(
  settings: AuditConsumerSettings,
  auditLogWriter: AuditLogWriter,
  commandFactory: AuditCommandFactory
)(implicit ec: ExecutionContext) {

  def process(envelope: TodoEventEnvelope): Future[AuditProcessingResult] =
    if (envelope.eventVersion != settings.supportedEventVersion) {
      Future.successful(UnsupportedVersionIgnored)
    } else if (!commandFactory.supports(envelope.eventType)) {
      Future.successful(UnsupportedEventIgnored)
    } else {
      auditLogWriter.writeIfNew(commandFactory.fromEvent(envelope))
    }
}
