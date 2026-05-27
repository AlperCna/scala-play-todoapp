package com.alper.todo.auditconsumer.model

sealed trait AuditConsumerRecordResult

object AuditConsumerRecordResult {
  case object Processed extends AuditConsumerRecordResult
  case object DuplicateIgnored extends AuditConsumerRecordResult
  case object UnsupportedVersionIgnored extends AuditConsumerRecordResult
  case object UnsupportedEventIgnored extends AuditConsumerRecordResult
  case object MalformedPayloadIgnored extends AuditConsumerRecordResult
}
