package com.alper.todo.auditconsumer.model

sealed trait AuditProcessingResult

object AuditProcessingResult {
  case object Processed extends AuditProcessingResult
  case object DuplicateIgnored extends AuditProcessingResult
  case object UnsupportedVersionIgnored extends AuditProcessingResult
  case object UnsupportedEventIgnored extends AuditProcessingResult
}
