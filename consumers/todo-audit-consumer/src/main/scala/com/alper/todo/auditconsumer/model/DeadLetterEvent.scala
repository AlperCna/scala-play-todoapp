package com.alper.todo.auditconsumer.model

import play.api.libs.json.{Json, OWrites}

import java.time.Instant

case class DeadLetterEvent(
  consumerName: String,
  originalTopic: String,
  originalKey: Option[String],
  originalPartition: Int,
  originalOffset: Long,
  reason: String,
  errorMessage: Option[String],
  rawPayload: String,
  receivedAt: Instant
)

object DeadLetterEvent {
  implicit val writes: OWrites[DeadLetterEvent] = Json.writes[DeadLetterEvent]
}
