package com.alper.todo.analyticsconsumer.model

sealed trait AnalyticsConsumerRecordResult

object AnalyticsConsumerRecordResult {
  case class Processed(result: AnalyticsProcessingResult) extends AnalyticsConsumerRecordResult
  case object MalformedPayloadIgnored extends AnalyticsConsumerRecordResult
}
