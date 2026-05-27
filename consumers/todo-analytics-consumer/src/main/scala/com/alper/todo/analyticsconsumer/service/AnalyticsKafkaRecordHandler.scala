package com.alper.todo.analyticsconsumer.service

import com.alper.todo.analyticsconsumer.json.TodoEventJson
import com.alper.todo.analyticsconsumer.model.AnalyticsConsumerRecordResult
import com.alper.todo.analyticsconsumer.model.AnalyticsConsumerRecordResult.{MalformedPayloadIgnored, Processed}
import play.api.libs.json.JsSuccess

import scala.concurrent.{ExecutionContext, Future}

class AnalyticsKafkaRecordHandler(processor: AnalyticsEventProcessor)(implicit ec: ExecutionContext) {

  def handle(rawValue: String): Future[AnalyticsConsumerRecordResult] =
    TodoEventJson.parse(rawValue) match {
      case JsSuccess(envelope, _) =>
        processor.process(envelope).map(Processed)
      case _ =>
        Future.successful(MalformedPayloadIgnored)
    }
}
