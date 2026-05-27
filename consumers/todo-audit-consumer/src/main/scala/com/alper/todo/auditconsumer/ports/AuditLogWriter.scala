package com.alper.todo.auditconsumer.ports

import com.alper.todo.auditconsumer.model.{AuditCommand, AuditProcessingResult}

import scala.concurrent.Future

trait AuditLogWriter {
  def writeIfNew(command: AuditCommand): Future[AuditProcessingResult]
}
