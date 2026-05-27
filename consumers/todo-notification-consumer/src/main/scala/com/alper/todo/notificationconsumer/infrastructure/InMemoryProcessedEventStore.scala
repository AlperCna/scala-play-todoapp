package com.alper.todo.notificationconsumer.infrastructure

import com.alper.todo.notificationconsumer.ports.ProcessedEventStore

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}

class InMemoryProcessedEventStore(implicit ec: ExecutionContext) extends ProcessedEventStore {

  private val processedIds = ConcurrentHashMap.newKeySet[UUID]()

  override def contains(eventId: UUID): Future[Boolean] =
    Future.successful(processedIds.contains(eventId))

  override def markProcessed(eventId: UUID): Future[Unit] = Future.successful {
    processedIds.add(eventId)
    ()
  }
}
