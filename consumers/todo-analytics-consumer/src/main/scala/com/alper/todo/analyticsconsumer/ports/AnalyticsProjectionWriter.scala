package com.alper.todo.analyticsconsumer.ports

import com.alper.todo.analyticsconsumer.model.{AnalyticsCommand, AnalyticsProcessingResult}

import scala.concurrent.Future

trait AnalyticsProjectionWriter {
  def writeIfNew(command: AnalyticsCommand): Future[AnalyticsProcessingResult]
}
