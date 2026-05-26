package com.alper.todo.notificationconsumer.model

sealed trait NotificationProcessingResult

object NotificationProcessingResult {
  case object Processed extends NotificationProcessingResult
  case object DuplicateIgnored extends NotificationProcessingResult
  case object UnsupportedVersionIgnored extends NotificationProcessingResult
  case object UnsupportedEventIgnored extends NotificationProcessingResult
  case object DisabledIgnored extends NotificationProcessingResult
}
