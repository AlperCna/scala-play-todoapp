package com.alper.todo.notificationconsumer.json

import com.alper.todo.notificationconsumer.model.{TodoEventEnvelope, TodoPayload}
import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.time.{Instant, LocalDateTime}
import java.util.UUID

object TodoEventJson {

  private val uuidReads: Reads[UUID] =
    Reads.StringReads.map(UUID.fromString)

  private val instantReads: Reads[Instant] =
    Reads.StringReads.map(Instant.parse)

  private val localDateTimeReads: Reads[LocalDateTime] =
    Reads.StringReads.map(LocalDateTime.parse)

  implicit val todoPayloadReads: Reads[TodoPayload] = (
    (__ \ "todoId").read[UUID](uuidReads) and
      (__ \ "title").read[String] and
      (__ \ "description").readNullable[String] and
      (__ \ "isCompleted").read[Boolean] and
      (__ \ "dueDate").readNullable[LocalDateTime](localDateTimeReads) and
      (__ \ "createdAt").read[LocalDateTime](localDateTimeReads) and
      (__ \ "updatedAt").readNullable[LocalDateTime](localDateTimeReads) and
      (__ \ "deletedAt").readNullable[LocalDateTime](localDateTimeReads) and
      (__ \ "tenantId").read[UUID](uuidReads) and
      (__ \ "userId").read[UUID](uuidReads)
    )(TodoPayload.apply _)

  implicit val todoEventEnvelopeReads: Reads[TodoEventEnvelope] = (
    (__ \ "eventId").read[UUID](uuidReads) and
      (__ \ "eventType").read[String] and
      (__ \ "eventVersion").read[Int] and
      (__ \ "occurredAt").read[Instant](instantReads) and
      (__ \ "tenantId").read[UUID](uuidReads) and
      (__ \ "userId").read[UUID](uuidReads) and
      (__ \ "entityType").read[String] and
      (__ \ "entityId").read[UUID](uuidReads) and
      (__ \ "correlationId").readNullable[String] and
      (__ \ "payload").read[TodoPayload]
    )(TodoEventEnvelope.apply _)

  def parseEnvelope(rawJson: String): JsResult[TodoEventEnvelope] =
    Json.parse(rawJson).validate[TodoEventEnvelope]
}
