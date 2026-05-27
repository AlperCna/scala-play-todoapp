package com.alper.todo.notificationconsumer.service

import com.alper.todo.notificationconsumer.json.TodoEventJson
import com.alper.todo.notificationconsumer.model.NotificationConsumerRecordResult
import com.alper.todo.notificationconsumer.model.NotificationConsumerRecordResult._
import play.api.libs.json.JsError

import scala.concurrent.{ExecutionContext, Future}

class NotificationKafkaRecordHandler(
  processor: NotificationEventProcessor
)(implicit ec: ExecutionContext) {

  def handle(rawJson: String): Future[NotificationConsumerRecordResult] =
    TodoEventJson.parseEnvelope(rawJson) match {
      case JsError(errors) =>
        println(s"[WARN] notification-consumer ignored malformed payload: ${JsError.toJson(errors)}")
        Future.successful(MalformedPayloadIgnored)

      case play.api.libs.json.JsSuccess(envelope, _) =>
        processor.process(envelope).map {
          case com.alper.todo.notificationconsumer.model.NotificationProcessingResult.Processed =>
            NotificationConsumerRecordResult.Processed
          case com.alper.todo.notificationconsumer.model.NotificationProcessingResult.DuplicateIgnored =>
            NotificationConsumerRecordResult.DuplicateIgnored
          case com.alper.todo.notificationconsumer.model.NotificationProcessingResult.UnsupportedVersionIgnored =>
            NotificationConsumerRecordResult.UnsupportedVersionIgnored
          case com.alper.todo.notificationconsumer.model.NotificationProcessingResult.UnsupportedEventIgnored =>
            NotificationConsumerRecordResult.UnsupportedEventIgnored
          case com.alper.todo.notificationconsumer.model.NotificationProcessingResult.DisabledIgnored =>
            NotificationConsumerRecordResult.DisabledIgnored
        }
    }
}
