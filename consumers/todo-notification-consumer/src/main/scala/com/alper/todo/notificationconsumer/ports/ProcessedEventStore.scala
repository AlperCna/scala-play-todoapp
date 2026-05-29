package com.alper.todo.notificationconsumer.ports

import java.util.UUID
import scala.concurrent.Future

trait ProcessedEventStore {
  def contains(eventId: UUID): Future[Boolean]
  def markProcessed(eventId: UUID, tenantId: UUID): Future[Unit]
}
