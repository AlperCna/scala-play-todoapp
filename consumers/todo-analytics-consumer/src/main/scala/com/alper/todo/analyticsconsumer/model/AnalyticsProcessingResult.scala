package com.alper.todo.analyticsconsumer.model

sealed trait AnalyticsProcessingResult

object AnalyticsProcessingResult {
  case object Processed extends AnalyticsProcessingResult
  case object DuplicateIgnored extends AnalyticsProcessingResult
  case object UnsupportedVersionIgnored extends AnalyticsProcessingResult
  case object UnsupportedEventIgnored extends AnalyticsProcessingResult
  case object MalformedPayloadIgnored extends AnalyticsProcessingResult
}
