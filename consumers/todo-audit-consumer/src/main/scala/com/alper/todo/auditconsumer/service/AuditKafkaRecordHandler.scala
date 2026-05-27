package com.alper.todo.auditconsumer.service

import com.alper.todo.auditconsumer.json.TodoEventJson
import com.alper.todo.auditconsumer.model.AuditConsumerRecordResult
import com.alper.todo.auditconsumer.model.AuditConsumerRecordResult._
import com.alper.todo.auditconsumer.model.AuditProcessingResult
import play.api.libs.json.JsError

import scala.concurrent.{ExecutionContext, Future}

class AuditKafkaRecordHandler(
  processor: AuditEventProcessor
)(implicit ec: ExecutionContext) {

  def handle(rawJson: String): Future[AuditConsumerRecordResult] =
    TodoEventJson.parseEnvelope(rawJson) match {
      case JsError(errors) =>
        println(s"[WARN] audit-consumer ignored malformed payload: ${JsError.toJson(errors)}")
        Future.successful(MalformedPayloadIgnored)

      case play.api.libs.json.JsSuccess(envelope, _) =>
        processor.process(envelope).map {
          case AuditProcessingResult.Processed                 => Processed
          case AuditProcessingResult.DuplicateIgnored          => DuplicateIgnored
          case AuditProcessingResult.UnsupportedVersionIgnored => UnsupportedVersionIgnored
          case AuditProcessingResult.UnsupportedEventIgnored   => UnsupportedEventIgnored
        }
    }
}
